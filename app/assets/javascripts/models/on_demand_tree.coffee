IdTree = require('models/id_tree').IdTree
LruPagingStrategy = require('models/lru_paging_strategy').LruPagingStrategy

DEFAULT_OPTIONS = {
  cache_size: 1000,
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
#     nodes = tree.get_node_children(node) # undefined if not loaded
#     nodeids = tree.id_tree.children[node.id] # valid for all nodes
#     tree.demand(id).done(-> tree.get_node_children(node)) # guaranteed
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
  # Let's suggest removing children instead.
  _id_to_first_leaf: (id) ->
    c = @id_tree.children

    _first_defined_child_id = (id) ->
      for child_id in c[id]
        return child_id if c[child_id]?
      undefined

    loop
      nextid = _first_defined_child_id(id)

      return id if !nextid?
      id = nextid

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
      leafid = this._id_to_first_leaf(id)
      this._remove_leaf_node(editable, leafid)
      removed += 1

      return removed if leafid == id || removed >= n

  _remove_n_nodes: (editable, n) ->
    while n > 0
      id = @_paging_strategy.find_id_to_free()
      n -= this._remove_up_to_n_nodes_starting_at_id(editable, n, id)

  _add_json: (json) ->
    return if !json.nodes.length

    @id_tree.edit (editable) =>
      # (This comes in handy later)
      frozen_ids = this._id_to_important_other_ids(json.nodes[0].id)

      added_ids = []

      # Actually add to the tree
      for node in json.nodes
        if !@id_tree.children[node.id]?
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
    @cache.resolve_deferred('node', id).done(this._add_json.bind(this))

  unload_node_children: (id) ->
    @id_tree.edit (editable) =>
      this._remove_leaf_node(editable, nodeid) for nodeid in @id_tree.loaded_descendents(id)
      undefined

  get_loaded_node_children: (node) ->
    _.compact(@nodes[child_id] for child_id in @id_tree.children[node.id])

  get_node_parent: (node) ->
    parent_id = @id_tree.parent[node.id]
    if parent_id? then @nodes[parent_id] else undefined

  remove_tag_from_node: (nodeid, tag) ->
    node = @nodes[nodeid]
    count = node.tagcounts[tag.id]
    this._remove_tagid_from_node_and_children(node, tag.id)

    parent = this.get_node_parent(node)
    this._add_tagcount_to_node_and_ancestors(parent, tag.id, -count) if parent?

    count

  _remove_tagid_from_node_and_children: (node, tagid) ->
    tagcounts = node.tagcounts
    delete tagcounts[tagid] if tagcounts[tagid]?

    for child_node in this.get_loaded_node_children(node)
      this._remove_tagid_from_node_and_children(child_node, tagid)
    undefined

  add_tag_to_node: (nodeid, tag) ->
    node = @nodes[nodeid]

    difference = this._add_tagid_to_node_and_children(node, tag.id)

    parent = this.get_node_parent(node)
    this._add_tagcount_to_node_and_ancestors(parent, tag.id, difference) if parent?

    difference

  _add_tagid_to_node_and_children: (node, tagid) ->
    tagcounts = node.tagcounts
    count_before = tagcounts[tagid] || 0
    count_after = node.doclist.n
    tagcounts[tagid] = count_after

    for child_node in this.get_loaded_node_children(node)
      this._add_tagid_to_node_and_children(child_node, tagid)

    count_after - count_before

  _add_tagcount_to_node_and_ancestors: (node, tagid, count) ->
    tagcounts = node.tagcounts
    tagcounts[tagid] ||= 0
    tagcounts[tagid] += count
    delete tagcounts[tagid] if tagcounts[tagid] == 0

    parent = this.get_node_parent(node)
    this._add_tagcount_to_node_and_ancestors(parent, tagid, count) if parent?

exports = require.make_export_object('models/on_demand_tree')
exports.OnDemandTree = OnDemandTree
