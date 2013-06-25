define [ 'underscore', './id_tree', './lru_paging_strategy' ], (_, IdTree, LruPagingStrategy) ->
  DEFAULT_OPTIONS = {
    cache_size: 5000,
  }

  # An incomplete tree structure.
  #
  # The tree depends upon a cache. It will call
  # `cache.resolve_deferred('node', [id])` and act upon the deferred JSON result.
  # The first call will be to `cache.resolve_deferred('root')`.
  #
  # Usage:
  #
  #     tree = new OnDemandTree()
  #     tree.demand_root() # will notify :added (with IDs) and :root
  #     deferred = tree.demand(id) # will demand node and fire :add if it's new
  #     node = tree.nodes[id] # will return node, only if present
  #     nodeids = tree.id_tree.children[node.id] # valid for all nodes
  #     tree.demand(id).done(-> tree.id_tree.children[node.id]) # guaranteed
  #     id_tree = tree.id_tree # for optimized algorithms (see `IdTree`)
  #
  # The tree will automatically remove unused nodes, as specified by
  # demand(id) and get_node(id). (To make a node more likely to survive cache
  # flushes, call tree.get_node(id).)
  #
  # Observers can handle :add, :remove, :remove-undefined on the id_tree to
  # maintain consistent views of the tree.
  #
  # Events happen after the fact. In particular, you cannot .get_node_children()
  # on removed nodes during :remove. (You *will* be notified of all the removals
  # in order from deepest to shallowest, though.)
  #
  # Options:
  #
  # * cache_size (default 1000): number of nodes to *not* remove
  class OnDemandTree
    constructor: (@cache, options={}) ->
      @id_tree = new IdTree()
      @nodes = {}
      @_paging_strategy = new LruPagingStrategy(options.cache_size || DEFAULT_OPTIONS.cache_size)

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

        is_leaf = true

        for childid in c[nodeid]
          if c[childid]?
            is_leaf = false
            visit_nodeid_at_depth(childid, depth + 1)

        leaf_nodeids.push(nodeid) if is_leaf

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
            ret.push(child) if c[child]? && child != id
        cur = p[cur]
      ret.push(@id_tree.root) if @id_tree.root != -1

      ret

    _remove_leaf_node: (editable, leafid) ->
      editable.remove(leafid)
      delete @nodes[leafid]
      @_paging_strategy.free(leafid)

    _remove_up_to_n_nodes_starting_at_id: (editable, n, id) ->
      removed = 0

      loop
        # Assume if a node isn't frozen, nothing below it is frozen
        deepest_leaf_nodeids = this._id_to_deep_descendent_ids(id)
        for nodeid in deepest_leaf_nodeids
          this._remove_leaf_node(editable, nodeid)
          removed += 1

        return removed if deepest_leaf_nodeids[0] == id || removed >= n

    _remove_n_nodes: (editable, n) ->
      while n > 0
        id = @_paging_strategy.find_id_to_free()
        n -= this._remove_up_to_n_nodes_starting_at_id(editable, n, id)

    _add_json: (json) ->
      return if !json.nodes.length

      @id_tree.edit (editable) =>
        # (This comes in handy later)
        top_added_id = json.nodes[0].id # this ID is already in the tree
        frozen_ids = this._id_to_important_other_ids(top_added_id)
        frozen_ids.push(top_added_id) if @nodes[top_added_id]?

        added_ids = []

        # Actually add to the tree
        for node in json.nodes
          if !@nodes[node.id]?
            @nodes[node.id] = node
            editable.add(node.id, node.children)
            added_ids.push(node.id)

        overflow_ids = []

        # Track the IDs we can, without overloading our paging strategy
        for id in added_ids
          if @_paging_strategy.is_full()
            overflow_ids.push(id)
          else
            @_paging_strategy.add(id)
        added_ids.splice(-overflow_ids.length)

        if overflow_ids.length
          # Our tree is over-sized. Let's find old nodes to remove.

          # Freeze the nodes we *don't* want to remove
          frozen_ids.push(id) for id in added_ids

          @_paging_strategy.freeze(id) for id in frozen_ids

          # Remove expendable nodes
          this._remove_n_nodes(editable, overflow_ids.length)

          # Unfreeze those important nodes
          @_paging_strategy.thaw(id) for id in frozen_ids

          # Now we have space for the new ones
          @_paging_strategy.add(id) for id in overflow_ids

        undefined

    demand_root: () ->
      @cache.resolve_deferred('root').done(this._add_json.bind(this))

    demand_node: (id) ->
      @cache.resolve_deferred('node', id)
        .done(this._add_json.bind(this))

    _collapse_node: (editable, id) ->
      c = @id_tree.children

      for childid in c[id]
        if c[childid]?
          this._collapse_node(editable, childid)
          editable.remove(childid)
          delete @nodes[childid]
          @_paging_strategy.free(childid)

    # "Collapse" a node (public-facing method)
    unload_node_children: (id) ->
      @id_tree.edit (editable) =>
        this._collapse_node(editable, id)

    get_loaded_node_children: (node) ->
      _.compact(@nodes[child_id] for child_id in @id_tree.children[node.id])

    get_root: ->
      id = @id_tree.root
      id? && @nodes[id] || undefined

    get_node_parent: (node) ->
      parent_id = @id_tree.parent[node.id]
      if parent_id? then @nodes[parent_id] else undefined

    rewrite_tag_id: (old_tagid, tagid) ->
      old_tagid_string = "#{old_tagid}"
      tagid_string = "#{tagid}"
      for __, node of @nodes
        tagcounts = (node.tagcounts ||= {})
        tagcount = tagcounts[old_tagid_string]
        if tagcount?
          delete tagcounts[old_tagid_string]
          tagcounts[tagid_string] = tagcount

      undefined
