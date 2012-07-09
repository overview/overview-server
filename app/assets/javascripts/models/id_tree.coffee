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
# The other notification is `remove`, which mirrors `add` but removes sub-nodes
# as well. Both will be called after an `edit`, if need be: first `add` and
# then `remove`.
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
      root: false,
    }

  has: (id) ->
    @children[id]?

  all: () ->
    +i for i, _ of @children

  edit: (callback) ->
    callback(@_editor)

    this._notify('add', @_edits.add) if @_edits.add.length
    this._notify('root', @root) if @_edits.root
    this._notify('remove', @_edits.remove) if @_edits.remove.length

    @_edits.add = []
    @_edits.remove = []
    @_edits.root = false

  _add: (id, children) ->
    if @root == -1
      @root = id
      @_edits.root = true
    else
      throw 'MissingNode' if !@parent[id]?

    @children[id] = children
    (@parent[child_id] = id for child_id in children)
    @_edits.add.push(id)

  _remove: (id) ->
    child_ids = []
    to_visit = @children[id]

    throw 'MissingNode' if !to_visit?

    while cur = to_visit.pop()
      child_ids.push(cur)
      if children = @children[cur]
        @_edits.remove.push(cur)
        (to_visit.push(child_id) for child_id in children)

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
