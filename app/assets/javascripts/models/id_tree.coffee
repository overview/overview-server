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
# Some invariants of the IdTree:
#
# * Any id that `children` points to has an inverse `parent` entry
# * The inverse is true, except nothing points to `root`.
# * `has()` returns whether the `children` entry is defined.
# * There are never any dangling `parent` entries.
# * There are never any dangling `children` entries.
# * There are never any loops
class IdTree
  constructor: () ->
    @root = -1
    @children = {}
    @parent = {}

    @_editor = {
      add: this._add.bind(this),
      remove: this._remove.bind(this),

      #_added: [],
      #_removed: [],
    }

  has: (id) ->
    @children[id]?

  all: () ->
    +i for i, _ of @children

  edit: (callback) ->
    callback(@_editor)

  _add: (id, children) ->
    if @root == -1
      @root = id
    else
      throw 'MissingNode' if !@parent[id]?

    @children[id] = children
    (@parent[child_id] = id for child_id in children)

  _remove: (id) ->
    child_ids = []
    to_visit = @children[id]

    throw 'MissingNode' if !to_visit?

    while cur = to_visit.pop()
      child_ids.push(cur)
      if children = @children[cur]
        (to_visit.push(child_id) for child_id in children)

    for child_id in child_ids
      delete @parent[child_id]
      delete @children[child_id]

    @root = -1 if @root == id

    delete @children[id]

exports = require.make_export_object('models/id_tree')
exports.IdTree = IdTree
