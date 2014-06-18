define [ './observable' ], (observable) ->
  # A special, fast tree which only stores IDs. The tree is made for speed.
  #
  # Modify the tree using batch operations. There are two key operations one
  # can execute in batch:
  #
  # * add edge: add an edge from a parent node to a child node
  # * remove node: remove an entire tree node
  #
  # The first edge added determines the root node.
  #
  # Create and edit the tree like this:
  #
  #     id_tree = new IdTree()
  #     id_tree.batchAdd (add) ->
  #       add(null, 1) # add a root node
  #       add(1, 2) # one edge
  #       add(1, 3) # another edge
  #       add(2, [4, 5]) # alternate API
  #       [ [4, 6], [4, 7], [4, 8] ].map(add.apply.bind({})) # getting creative
  #
  #     id_tree.batchRemove (remove) ->
  #       remove(1) # exception: can only remove child nodes
  #       remove(8)
  #       remove([7, 6])
  #       remove([5, 4, 3, 2])
  #       remove(1)
  #
  # When editing a tree, follow these rules:
  #
  # * Add a parent before adding its children
  # * Add all edges from a given parent in a single batch
  # * Delete all children of a given parent in a single batch
  # * Do not create any cycles
  # * Use the correct types. Pass 1, for instance, as opposed to "1"
  #
  # Watch for tree changes like this:
  #
  #     id_tree = new IdTree()
  #     id_tree.observe 'change', (changes) ->
  #       console.log("New root ID", changes.root) # may be undefined
  #       console.log("Added child nodes", changes.added) # ordered: parents are always added before children
  #       console.log("Removed nodes", changes.removed) # ordered: children are always removed before parents
  #
  # Now, to read the tree, use this interface, which is optimized for speed:
  #
  #     id_tree.root # 1
  #     id_tree.has(1) # true: the node is a parent
  #     id_tree.has(6) # true: the node is a child
  #     id_tree.has(9) # false: never added
  #     id_tree.children[1] # [2, 3]
  #     id_tree.children[4] # []
  #     id_tree.children[6] # undefined
  #     id_tree.parent[6] # 4
  #     id_tree.parent[3] # 1
  #     id_tree.parent[1] # null
  #     id_tree.descendents(2) # [ 4, 5, 6, 7, 8 ]
  class IdTree
    observable(this)

    constructor: ->
      @reset()

    reset: ->
      @root = null
      @children = {}
      @parent = {}
      @_notify('reset')

    # Returns true iff the node's list of children is loaded.
    #
    # Running time: O(1)
    has: (id) ->
      @children[id]?

    # Returns all node IDs for which the list of children is loaded.
    all: ->
      +i for i, _ of @parent

    # Walk the entire tree, post-order
    walk: (callback) -> @walkFrom(@root, callback)

    # Walk the subtree rooted at id, post-order
    walkFrom: (id, callback) ->
      idKey = String(id)
      throw 'InvalidWalkRoot' if idKey not of @parent
      if idKey of @children
        @walkFrom(childId, callback) for childId in @children[idKey]
      callback(id)
      undefined

    # Walk the entire tree, pre-order
    walkPreorder: (callback) -> @walkFromPreorder(@root, callback)

    # Walk the subtree rooted at id, pre-order
    walkFromPreorder: (id, callback) ->
      idKey = String(id)
      throw 'InvalidWalkRoot' if idKey not of @parent
      callback(id)
      if idKey of @children
        @walkFromPreorder(childId, callback) for childId in @children[idKey]
      undefined

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

    batchAdd: (callback) ->
      oldRoot = @root
      added = []

      add = (parent, child) =>
        if parent is null
          throw 'RootAlreadyExists' if @root isnt null
          @root = child
        else
          parentKey = String(parent)
          throw 'MissingParent' if parentKey not of @parent
          (@children[parentKey] ||= []).push(child)

        childKey = String(child)
        @parent[childKey] = parent

        added.push(child)
        undefined

      addWrapper = (parent, childOrChildren) ->
        if Array.isArray(childOrChildren)
          for child in childOrChildren
            add(parent, child)
        else
          add(parent, childOrChildren)
        undefined

      callback(addWrapper)

      changes = { added: added }
      changes.root = @root if @root != oldRoot
      @_notify('change', changes)

    batchRemove: (callback) ->
      oldRoot = @root
      removed = []

      remove = (child) =>
        childKey = String(child)

        throw 'NoNodeToRemove' if childKey not of @parent

        parent = @parent[childKey]
        delete @parent[childKey]

        if parent is null
          throw 'InvalidRootToRemove' if child != @root
          @root = null
        else
          parentKey = String(parent)
          # Assume we're deleting all in the same batchRemove()
          delete @children[parentKey] if parentKey of @children

        removed.push(child)
        undefined

      removeWrapper = (idOrIds) ->
        if Array.isArray(idOrIds)
          for id in idOrIds
            remove(id)
        else
          remove(idOrIds)
        undefined

      callback(removeWrapper)

      changes = { removed: removed }
      changes.root = @root if @root != oldRoot
      @_notify('change', changes)

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
