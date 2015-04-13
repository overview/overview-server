define [
  'underscore'
  'jquery'
  'rsvp'
  'backbone'
  './id_tree'
  './lru_paging_strategy'
], (_, $, RSVP, Backbone, IdTree, LruPagingStrategy) ->
  DEFAULT_OPTIONS = {
    cache_size: 5000,
  }

  # An incomplete tree structure.
  #
  # This class serves the following functions:
  #
  # * It requests nodes from the server
  # * It stores the server's literal JSON responses
  # * It automatically removes nodes from the tree when there are too many
  #
  # In particular, this class does _not_ store the tree structure. It
  # does provide getChildren() and getParent() convenience methods to
  # look up the JSON objects above and below a particular node. But it doesn't
  # trigger any event when the return values of those functions would change.
  #
  # The tree depends upon a cache. It will call
  # `cache.resolve_deferred('node', [id])` and act upon the deferred JSON result.
  # The first call will be to `cache.resolve_deferred('root')`.
  #
  # Usage:
  #
  #     tree = new OnDemandTree()
  #     tree.demandRoot() # will notify :added (with IDs) and :root
  #     deferred = tree.demandNode(id) # will demand node and fire :add if it's new
  #     node = tree.nodes[id] # will return node, only if present
  #     nodeids = tree.id_tree.children[node.id] # valid for all nodes
  #     tree.demand(id).then(-> tree.id_tree.children[node.id]) # guaranteed
  #     id_tree = tree.id_tree # for optimized algorithms (see `IdTree`)
  #
  # The tree will automatically remove unused nodes, as specified by
  # demand(id) and get_node(id). (To make a node more likely to survive cache
  # flushes, call tree.get_node(id).)
  #
  # Observers can handle :change on the id_tree to maintain consistent views
  # of the tree.
  #
  # Events happen after the fact. In particular, you cannot .get_node_children()
  # on removed nodes during :remove. (You *will* be notified of all the removals
  # in order from deepest to shallowest, though.)
  #
  # Options:
  #
  # * cache_size (default 5000): number of nodes to *not* remove
  class OnDemandTree
    _.extend(@::, Backbone.Events)
    constructor: (@state, @view, options={}) ->
      @transactionQueue = @state.transactionQueue
      @options = options

      @id_tree = new IdTree()
      @nodes = {}
      @_paging_strategy = new LruPagingStrategy(@options.cache_size || DEFAULT_OPTIONS.cache_size)
      @demandRoot()
        .then(=> @view.set(rootNodeId: @id_tree.root))

    remove: ->
      n.off() for __, n of @nodes
      undefined

    getNode: (id) -> @nodes[id]
    getChildren: (id) ->
      id = id.id? && id.id || id
      childIds = @id_tree.children[id]
      childIds?.map((id) => @nodes[id]) || undefined
    getParent: (id) ->
      id = id.id? && id.id || id
      parentId = @nodes[id]?.parentId
      parentId && @nodes[parentId] || null

    # Our @_paging_strategy suggests a node to remove, but it might be high up.
    # Let's suggest the lowest-possible nodes.
    #
    # In a tree like this:
    #
    #             1
    #      2      3      4
    #   6   7 8
    # 9 10
    #
    # _id_to_deep_descendent_id(1) will return [9, 10], because they are the
    # deepest leaf nodes and thus the best candidates for removal.
    _id_to_deep_descendent_ids: (id) ->
      c = @id_tree.children

      d = {} # depth of each node
      max_depth = -1
      leaf_nodeids = []

      visit_nodeid_at_depth = (nodeid, depth) ->
        d[nodeid] = depth
        max_depth = depth if depth > max_depth

        if nodeid of c
          for childid in c[nodeid]
            visit_nodeid_at_depth(childid, depth + 1)
        else
          leaf_nodeids.push(nodeid)

        undefined

      visit_nodeid_at_depth(id, 0)

      leaf_nodeids.filter((leafid) -> d[leafid] == max_depth)

    # Returns IDs that relate to the given one.
    #
    # For instance, in this tree:
    #
    #               1
    #      2        3          4
    #    5 6 7    8 9 10    11 12 13
    #
    # The "important" IDs to 6 are 5, 7, 2, 3, 4 and 1: all siblings, parents,
    # aunts and uncles from all tree levels. Only loaded-node IDs will be
    # returned.
    _id_to_important_other_ids: (id) ->
      ret = []
      c = @id_tree.children
      p = @id_tree.parent

      cur = id
      while cur?
        children = c[cur]
        if children?
          for child in children
            ret.push(child) if child != id
        cur = p[cur]
      ret.push(@id_tree.root) if @id_tree.root != null

      ret

    _remove_leaf_node: (idTreeRemove, leafid) ->
      idTreeRemove(leafid)
      @nodes[leafid].off()
      delete @nodes[leafid]
      @_paging_strategy.free(leafid)

    _remove_up_to_n_nodes_starting_at_id: (idTreeRemove, n, id) ->
      removed = 0

      loop
        # Assume if a node isn't frozen, nothing below it is frozen
        deepest_leaf_nodeids = this._id_to_deep_descendent_ids(id)
        for nodeid in deepest_leaf_nodeids
          this._remove_leaf_node(idTreeRemove, nodeid)
          removed += 1

        return removed if deepest_leaf_nodeids[0] == id || removed >= n

    _remove_n_nodes: (idTreeRemove, n) ->
      while n > 0
        id = @_paging_strategy.find_id_to_free()
        n -= this._remove_up_to_n_nodes_starting_at_id(idTreeRemove, n, id)

    _add_json: (json) ->
      return if !json.nodes.length

      # We'll first add the nodes we've received. If we've gone over our paging
      # limit, then we'll then remove excess nodes.

      added_ids = []
      overflow_ids = []

      # Actually add to the tree
      @id_tree.batchAdd (idTreeAdd) =>
        for node in json.nodes when (!node.parentId? || node.parentId of @nodes) and node.id not of @nodes
          _.extend(node, Backbone.Events)
          @nodes[node.id] = node
          idTreeAdd(node.parentId, node.id)
          added_ids.push(node.id)

      # Track the IDs we can, without overloading our paging strategy
      for id in added_ids
        if @_paging_strategy.is_full()
          overflow_ids.push(id)
        else
          @_paging_strategy.add(id)
      added_ids.splice(-overflow_ids.length)

      if overflow_ids.length
        # Our tree is over-sized. Let's find old nodes to remove.

        # For paging, figure out frozen_ids, the IDs we must not free. These
        # are all ancestors and uncles of the nodes we've added
        overflowIdSet = {}
        overflowIdSet[id] = null for id in overflow_ids
        frozenIdSet = {}
        for id in added_ids.concat(overflow_ids)
          loop
            parentId = @id_tree.parent[id]
            break if parentId is null
            siblingIds = @id_tree.children[parentId]
            (frozenIdSet[siblingId] = null) for siblingId in siblingIds
            id = parentId
        frozenIdSet[@id_tree.root] = null
        frozen_ids = (id for id, __ of frozenIdSet when id not of overflowIdSet)

        @_paging_strategy.freeze(id) for id in frozen_ids

        # Remove expendable nodes
        @id_tree.batchRemove (idTreeRemove) =>
          this._remove_n_nodes(idTreeRemove, overflow_ids.length)

        # Unfreeze those important nodes
        @_paging_strategy.thaw(id) for id in frozen_ids

        # Now we have space for the new ones
        @_paging_strategy.add(id) for id in overflow_ids

        undefined

    demandRoot: -> @_demand("nodes")
    demandNode: (id) ->
      if @nodes[id]?.isLeaf || @id_tree.children[id]?
        # it's already loaded
        RSVP.resolve(@nodes[id] for id in (@id_tree.children[id] || []))
      else
        @_demand("nodes/#{id}")

    _demand: (arg) ->
      @transactionQueue.ajax
        debugInfo: 'OnDemandTree._demand'
        type: 'get'
        url: "/trees/#{@view.get('id')}/#{arg}.json"
        success: (json) =>
          @_add_json(json)
          if @highlightJson
            @_refreshHighlightCounts(@highlightJson, (n.id for n in json.nodes), false)

    _collapse_node: (idTreeRemove, id) ->
      idsToRemove = []
      @id_tree.walkFrom(id, (x) -> idsToRemove.push(x) if x != id)

      for idToRemove in idsToRemove
        @_paging_strategy.free(idToRemove)
        idTreeRemove(idToRemove)
        @nodes[idToRemove].off()
        delete @nodes[idToRemove]

      undefined

    # "Collapse" a node (public-facing method)
    unloadNodeChildren: (id) ->
      @id_tree.batchRemove (idTreeRemove) =>
        this._collapse_node(idTreeRemove, id)

    get_loaded_node_children: (node) ->
      _.compact(@nodes[child_id] for child_id in @id_tree.children[node.id])

    getNode: (id) -> @nodes[id]

    getRoot: ->
      id = @id_tree.root
      id? && @getNode(id) || undefined

    getNodeParent: (node) ->
      parent_id = @id_tree.parent[node.id]
      if parent_id? then @nodes[parent_id] else undefined

    get_node: (id) -> @getNode(id)
    get_root: -> @getRoot()
    get_node_parent: (node) -> @getNodeParent(node)

    saveNode: (node, newAttributes) ->
      for k, v of newAttributes
        node[k] = v

      node.trigger('change', node)

      nodeJson = _.pick(node, 'description')

      @transactionQueue.ajax
        type: 'POST'
        url: "/trees/#{@view.get('id')}/nodes/#{node.id}"
        data: nodeJson
        success: => @id_tree.batchAdd(->) # refresh
        debugInfo: 'OnDemandTree.saveNode'

    setHighlightJson: (json) ->
      return if _.isEqual(@highlightJson, json)

      @highlightJson = json

      # drop existing counts
      (node.highlightCount = null) for __, node of @nodes

      @refreshHighlightCountsOnCurrentNodes()

    refreshHighlightCountsOnCurrentNodes: ->
      if @highlightJson
        @_refreshHighlightCounts(@highlightJson, Object.keys(@nodes), true)

    _refreshHighlightCounts: (json, nodeIds, forceRefresh) ->
      return if !nodeIds?.length

      @transactionQueue.ajax
        type: 'POST'
        url: "/documentsets/#{@state.documentSetId}/document-nodes/count-by-node"
        data: _.extend({ countNodes: nodeIds.join(','), refresh: forceRefresh }, json) # at the time the transaction was _scheduled_
        debugInfo: 'OnDemandTree._refreshHighlightCounts'
        success: (data) =>
          # data is an Object of counts keyed by id
          for id in nodeIds
            node = @nodes[id]
            continue if !node? # it might have been unloaded since
            node.highlightCount = data[id] || 0

          @id_tree.batchAdd(->) # trigger update
