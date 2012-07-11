IdTree = require('models/id_tree').IdTree
LruPagingStrategy = require('models/lru_paging_strategy').LruPagingStrategy

DEFAULT_OPTIONS = {
  cache_size: 2000
}

# An incomplete tree structure.
#
# The tree depends upon a resolver. It will call
# `resolver.get_deferred('node', [id])` and act upon the deferred JSON result.
# The first call will be to `reoslver.get_deferred('root')`.
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
# * cache_size (default 100): number of nodes to *not* remove
class OnDemandTree
  constructor: (@resolver, options={}) ->
    @id_tree = new IdTree()
    @nodes = {}
    @height = 0
    @_paging_strategy = new LruPagingStrategy(options.cache_size || DEFAULT_OPTIONS.cache_size)

  # Our @_paging_strategy suggests a node to remove. We can add logic, though:
  # let's remove the minimum possible--a leaf--instead of the one it suggests.
  _id_to_first_leaf: (id) ->
    nextid = id
    c = @id_tree.children
    p = @id_tree.parent
    n = @nodes

    loop
      id = nextid
      nextid = undefined

      children = c[id]
      if children? && children[0]?
        for childid in children
          if n[childid]? && !@_paging_strategy.is_frozen(childid)
            nextid = childid
            break

      return id if !nextid?

  # Returns IDs that relate to the given one.
  #
  # For instance, in this tree:
  #
  #               1
  #      2        3          4
  #    5 6 7    8 9 10    11 12 13
  #
  # The "important" IDs to 6 are 5, 6, 7, 2, 3, 4 and 1: all siblings, parents,
  # aunts and uncles from all tree levels. Only loaded-node IDs will be
  # returned.
  _id_to_important_ids: (id) ->
    ret = []
    c = @id_tree.children
    p = @id_tree.parent
    n = @nodes

    while id?
      children = c[id]
      if children?
        for child in children
          ret.push(child) if n[child]?
      id = p[id]
    ret.push(@id_tree.root) if @id_tree.root != -1

    ret

  _add_json: (json) ->
    freeze_ids = this._id_to_important_ids(json.nodes[0].id)

    @_paging_strategy.freeze(x) for x in freeze_ids

    @id_tree.edit (editable) =>
      for node in (json.nodes || [])
        oldid = @_paging_strategy.find_id_to_free_if_full() # throws AllPagesFrozen
        if oldid?
          oldid = this._id_to_first_leaf(oldid)
          @_paging_strategy.free(oldid)
          delete @nodes[oldid]
          editable.remove(oldid)

        @_paging_strategy.add(node.id)
        @_paging_strategy.freeze(node.id)
        freeze_ids.push(node.id)
        @nodes[node.id] = node
        editable.add(node.id, node.children)

        this._refresh_height()

    @_paging_strategy.thaw(x) for x in freeze_ids

    undefined

  _refresh_height: () ->
    # bulky, but understandable and O(n)
    return @height = 0 if @id_tree.root == -1

    p = @id_tree.parent
    d = {} # id -> depth
    d[@id_tree.root] = 1

    id_to_depth = (id) ->
      loops = 0
      parent_id = id

      loop
        if d[parent_id]?
          return d[id] = loops + d[parent_id]

        parent_id = p[parent_id]
        loops += 1

    max_depth = 1
    for child_id, _ of p
      depth = id_to_depth(child_id)
      max_depth = depth if depth > max_depth

    @height = max_depth

  demand_root: () ->
    @resolver.get_deferred('root').done(this._add_json.bind(this))

  demand_node: (id) ->
    @resolver.get_deferred('node', id).done(this._add_json.bind(this))

exports = require.make_export_object('models/on_demand_tree')
exports.OnDemandTree = OnDemandTree
