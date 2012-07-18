observable = require('models/observable').observable

# A tree of animation nodes which tracks an OnDemandTree
#
# Callers need only create the AnimatedTree, pointed at an OnDemandTree,
# and they'll get the following:
#
# * An update() method, to change properties by an animation step
# * A needs_update() method, which says whether update() would do something
# * A @root property, which is suitable for drawing
# * A @animated_height property, which looks like { current: 3 } when H=3
# * A :needs-update signal, fired when needs_update() goes from false to true
#
# The root is either undefined (for an unloaded tree) or a collection of nodes
# that look like this:
#
# {
#   id: Number,
#   loaded: Boolean,
#   selected: Boolean,
#   num_documents: [property] Number of documents (if not loaded, an estimate),
#   loaded_animation_fraction: [property] 1 when loaded, 0 otherwise,
#   selected_animation_fraction: [property] 1 when selected, 0 otherwise,
#   children: [ list of nodes ] always empty when loaded_animation_fraction=0,
# }
#
# Each property marked [property] looks like this: { current: X }. The values
# change when update() is called.
class AnimatedTree
  observable(this)

  constructor: (@on_demand_tree, @selection, @animator) ->
    @id_tree = @on_demand_tree.id_tree
    @root = undefined # node with children property pointing to other nodes
    @animated_height = { current: 0 }
    @_nodes = {} # id => node
    @_last_selected_nodes = @selection.nodes

    this._attach()

  needs_update: () -> @animator.needs_update()
  update: () -> @animator.update()

  _attach: () ->
    @id_tree.observe 'edit', (changes) =>
      if @root?.id isnt @id_tree.root && @id_tree.root != -1
        this._load()
      else
        this._change(changes)

    @selection.observe(this._refresh_selection.bind(this))

  _maybe_notifying_needs_update: (callback) ->
    old_needs_update = this.needs_update()
    callback.apply(this)
    this._notify('needs-update') if !old_needs_update && this.needs_update()

  _refresh_selection: () ->
    this._maybe_notifying_needs_update ->
      old_selection = @_last_selected_nodes
      new_selection = @selection.nodes

      time = Date.now()

      removed = {}
      removed[oldid] = true for oldid in old_selection
      added = {}
      for newid in new_selection
        if removed[newid]?
          delete removed[newid] # it hasn't been added or removed
        else
          added[newid] = true

      for id, _ of removed
        node = @_nodes[id]
        if node?
          node.selected = false
          @animator.animate_object_properties(node, { selected_animation_fraction: 0 }, undefined, time)

      for id, _ of added
        node = @_nodes[id]
        if node?
          node.selected = true
          @animator.animate_object_properties(node, { selected_animation_fraction: 1 }, undefined, time)

      @_last_selected_nodes = new_selection

  _load: () ->
    c = @id_tree.children

    @root = this._create_node(@id_tree.root)
    @_nodes[@root.id] = @root

    parent_node_queue = [ @root ]

    while parent_node_queue.length
      parent_node = parent_node_queue.pop()
      child_ids = c[parent_node.id]

      loaded_documents = 0
      unloaded_children = []

      for child_id in child_ids
        if c[child_id]?
          node = this._create_node(child_id)
          loaded_documents += node.num_documents.current
          parent_node_queue.push(node)
        else
          node = this._create_undefined_node(child_id)
          unloaded_children.push(node)

        @_nodes[node.id] = node
        parent_node.children.push(node)

      if unloaded_children.length > 0
        # Spread the missing documents over the number of unloaded children
        num_unloaded_documents = parent_node.num_documents.current - loaded_documents
        num_per_unloaded_child = num_unloaded_documents / unloaded_children.length
        u.num_documents.current = num_per_unloaded_child for u in unloaded_children

    @animated_height.current = @on_demand_tree.height
    this._notify('needs-update')

  _change: (changes) ->
    this._maybe_notifying_needs_update ->
      time = Date.now()

      this._animate_remove_undefined_node(id, time) for id in changes.remove_undefined
      this._animate_unload_node(id, time) for id in changes.remove
      this._animate_load_node(id, time) for id in changes.add

      @animator.animate_object_properties(this, { animated_height: @on_demand_tree.height}, undefined, time)

  _animate_load_node: (id, time) ->
    node = @_nodes[id] # exists, but we know loaded = false
    real_node = @on_demand_tree.nodes[id]
    node.loaded = true
    @animator.animate_object_properties(node, {
      loaded_animation_fraction: 1,
      num_documents: real_node.doclist.n,
    }, undefined, time)

    child_ids = @id_tree.children[id]
    for child_id in child_ids
      # Even if the nodes are loaded, we'll add them here as unloaded nodes,
      # then we'll switch them to loaded in later _animate_load_node() calls.
      child_node = this._create_undefined_node(child_id)
      @_nodes[child_node.id] = child_node
      child_node.num_documents.current = real_node.doclist.n / child_ids.length
      node.children.push(child_node)

    this._animate_update_node_sibling_num_documents(id, time)

  _animate_update_node_sibling_num_documents: (id, time) ->
    parent_id = @id_tree.parent[id]
    return if !parent_id? # root node

    n = @on_demand_tree.nodes

    sibling_undefined_nodes = []
    sibling_documents = n[parent_id].doclist.n

    for sibling_node in @_nodes[parent_id].children
      if sibling_node.loaded
        sibling_documents -= n[sibling_node.id].doclist.n
      else
        sibling_undefined_nodes.push(sibling_node)

    if sibling_undefined_nodes.length > 0
      nd = sibling_documents / sibling_undefined_nodes.length
      for node in sibling_undefined_nodes
        @animator.animate_object_properties(node, { num_documents: nd }, undefined, time)

  _animate_remove_undefined_node: (id, time) ->
    node = @_nodes[id]
    @animator.animate_object_properties(node, { loaded_animation_fraction: 0 }, undefined, time)
    # We'll delete it from @_nodes in the parent's removal. We know the parent
    # is being unloaded.

  _animate_unload_node: (id, time) ->
    node = @_nodes[id]
    node.loaded = false
    @animator.animate_object_properties(node, { loaded_animation_fraction: 0 }, =>
      delete @_nodes[child.id] for child in node.children
      @_nodes[id].children = []
    , time)

  _create_node: (id) ->
    n = @on_demand_tree.nodes[id]
    selected = @selection.includes('node', id)
    {
      id: id,
      loaded: true,
      loaded_animation_fraction: { current: 1 },
      selected: selected,
      selected_animation_fraction: { current: selected && 1 || 0 },
      num_documents: { current: n.doclist.n },
      children: [],
    }

  _create_undefined_node: (id) ->
    selected = @selection.includes('node', id)
    {
      id: id,
      loaded: false,
      loaded_animation_fraction: { current: 0 },
      selected: selected,
      selected_animation_fraction: { current: selected && 1 || 0 },
      num_documents: { current: 0 },
      children: [],
    }

exports = require.make_export_object('models/animated_tree')
exports.AnimatedTree = AnimatedTree
