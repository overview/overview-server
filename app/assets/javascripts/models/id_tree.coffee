observable = require('models/observable').observable

# A special, fast tree which only stores IDs
#
# This tree is made for speed. Inserts do not create any objects, and it
# only notifies observers of batch operations. Its most important lookup
# methods aren't methods at all: they're hash lookups.
#
# To create:
#
#     id_tree = new IdTree()
#     id_tree.edit (editable) ->
#       editable.add(1, [2, 3])
#       editable.add(2, [4, 5])
#       editable.add(3, [6, 7])
#       editable.add(4, [])
#
# Observers will be notified of two changes:
#
# 1. `root`, which will notify that `root` is now `1`
# 2. `add`, which will notify that [1, 2, 3, 4] were added
#
# The other notifications are `remove` and `remove-undefined`, which mirror
# `add` but remove sub-nodes as well. They will be called after an `edit`, if
# need be: first `add`, then `remove-undefined`, and finally `remove`. The
# order of IDs is consistent: the `add` list ends with the deepest nodes, and
# the `remove` lists begin with them.
#
# Finally, `edit` is notified after any change.
#
# Now, to read the tree, use this interface, which is optimized for speed:
#
#     id_tree.root # 1
#     id_tree.has(1) # true
#     id_tree.has(6) # false
#     id_tree.children[1] # [2, 3]
#     id_tree.children[4] # []
#     id_tree.children[6] # undefined
#     id_tree.parent[6] # 3, even though node 6 isn't there
#     id_tree.parent[3] # 1
#     id_tree.parent[1] # undefined
#     id_tree.loaded_descendents(1) # [ 4, 2, 3 ]
#
# Some invariants of the IdTree, true before/after every method call:
#
# * Any id that `children` points to has an inverse `parent` entry
# * The inverse is true, except nothing points to `root`. (This means every
#   node has a path back to the root.)
# * `has()` returns whether the `children` entry is defined.
#
# And some undefined behavior:
#
# * Loops (i.e., a non-tree)
# * Re-adding a node that was just removed, all in the same edit
class IdTree
  observable(this)

  constructor: () ->
    @root = -1
    @children = {}
    @parent = {}

    @_editor = {
      add: this._add.bind(this),
      remove: this._remove.bind(this),
    }
    @_edits = {
      add: [],
      remove: [],
      remove_undefined: [],
      root: undefined,
    }

  # Returns true iff the node's list of children is loaded.
  #
  # Running time: O(1)
  has: (id) ->
    @children[id]?

  # Returns all node IDs for which the list of children is loaded.
  #
  # Running time: O(n), where n is the number of nodes in the tree.
  all: () ->
    +i for i, _ of @children

  # Returns true iff id1 is higher than id2 in the tree.
  #
  # Running time: O(h), where h is the height of the tree.
  is_id_ancestor_of_id: (id1, id2) ->
    throw 'MissingNode' if !@parent[id1]? && id1 != @root
    throw 'MissingNode' if !@parent[id2]? && id2 != @root

    while @parent[id2]?
      return true if @parent[id2] == id1
      id2 = @parent[id2]

    false

  edit: (callback) ->
    callback(@_editor)

    this._notify('add', @_edits.add) if @_edits.add.length
    this._notify('root', @root) if @_edits.root
    this._notify('remove-undefined', @_edits.remove_undefined) if @_edits.remove_undefined.length
    this._notify('remove', @_edits.remove) if @_edits.remove.length

    this._notify('edit', {
      add: @_edits.add,
      root: @_edits.root,
      remove_undefined: @_edits.remove_undefined,
      remove: @_edits.remove
    })

    @_edits.add = []
    @_edits.remove = []
    @_edits.remove_undefined = []
    @_edits.root = undefined

  _add: (id, children) ->
    throw 'NodeAlreadyExists' if @children[id]?

    if @root == -1
      @root = id
      @_edits.root = true
    else
      throw 'MissingNode' if !@parent[id]?

    @children[id] = children
    (@parent[child_id] = id for child_id in children)
    @_edits.add.push(id)

  loaded_descendents: (id) ->
    return undefined if !@children[id]?

    ret = []
    to_visit = @children[id].slice(0)

    # Breadth-first search
    while cur = to_visit.shift()
      children = @children[cur]
      if children? # may even be empty
        ret.unshift(cur)
        to_visit.push(child_id) for child_id in children

    ret

  _remove: (id) ->
    child_ids = []
    to_visit = @children[id]

    throw 'MissingNode' if !to_visit?

    # Breadth-first search
    while cur = to_visit.shift()
      child_ids.push(cur)
      if children = @children[cur]
        @_edits.remove.unshift(cur) # @_edits.remove is deepest-to-shallowest
        (to_visit.push(child_id) for child_id in children)
      else
        @_edits.remove_undefined.unshift(cur)

    for child_id in child_ids
      delete @parent[child_id]
      delete @children[child_id]

    if @root == id
      @root = -1
      @_edits.root = true

    delete @children[id]
    @_edits.remove.push(id)

exports = require.make_export_object('models/id_tree')
exports.IdTree = IdTree
