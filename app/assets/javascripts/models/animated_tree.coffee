observable = require('models/observable').observable

# A tree of animation nodes which tracks an OnDemandTree
#
# Callers need only create the AnimatedTree, pointed at an OnDemandTree,
# and they'll get the following:
#
# * An update() method, to change properties by an animation step
# * A needs_update() method, which says whether update() would do something
# * A @root property, which is suitable for drawing
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

  constructor: (@on_demand_tree, @state, @animator) ->
    @id_tree = @on_demand_tree.id_tree
    @root = undefined # node with children property pointing to other nodes
    @_nodes = {} # id => node
    @_last_selected_nodes = @state.selection.nodes
    @_needs_update = false

    this._attach()

  set_needs_update: () -> this._maybe_notifying_needs_update(->)
  needs_update: () -> @_needs_update || @animator.needs_update()
  update: () ->
    @_needs_update = false
    @animator.update()

  _attach: () ->
    @id_tree.observe 'edit', (changes) =>
      if @root?.id isnt @id_tree.root && @id_tree.root != -1
        this._load()
      else
        this._change(changes)

    @state.observe('selection-changed', this._refresh_selection.bind(this))

  _maybe_notifying_needs_update: (callback) ->
    old_needs_update = this.needs_update()
    callback.apply(this)
    this._notify('needs-update') if !old_needs_update && this.needs_update()

  _refresh_selection: () ->
    this._maybe_notifying_needs_update ->
      old_selection = @_last_selected_nodes
      new_selection = @state.selection.nodes

      time = Date.now()

      removed = _.difference(old_selection, new_selection)
      added = _.difference(new_selection, old_selection)

      for id in removed
        node = @_nodes[id]
        if node?
          node.selected = false
          @animator.animate_object_properties(node, { selected_animation_fraction: 0 }, undefined, time)

      for id in added
        node = @_nodes[id]
        if node?
          node.selected = true
          @animator.animate_object_properties(node, { selected_animation_fraction: 1 }, undefined, time)

      @_last_selected_nodes = new_selection

  _load: () ->
    @root = this._create_node(@id_tree.root)
    @_nodes[@root.id] = @root

    parent_node_queue = [ @root ]

    while parent_node_queue.length
      parent_node = parent_node_queue.pop()
      nd = this._node_child_ids_to_num_documents(parent_node)

      for child_id in @id_tree.children[parent_node.id]
        node = this._create_node(child_id)
        if node.loaded
          parent_node_queue.push(node)
        else
          node.num_documents.current = nd[child_id]

        parent_node.children.push(node)
        @_nodes[node.id] = node

    this._notify('needs-update')

  _change: (changes) ->
    this._maybe_notifying_needs_update ->
      @_needs_update = true

      time = Date.now()

      this._animate_remove_undefined_node(id, time) for id in changes.remove_undefined
      this._animate_unload_node(id, time) for id in changes.remove
      this._animate_load_node(id, time) for id in changes.add

  _animate_load_node: (id, time) ->
    node = @_nodes[id] # exists, but we know loaded = false

    if node.loaded == true
      throw "Trying to load #{node.id} but it is already loaded..."

    real_node = @on_demand_tree.nodes[id]
    node.loaded = true
    node.tagcounts = real_node.tagcounts
    @animator.animate_object_properties(node, {
      loaded_animation_fraction: 1,
    }, undefined, time)

    # Update all num_documents at this level, including this node
    this._animate_update_node_sibling_num_documents(id, time)

    # Create child nodes, and set their num_documents
    nd = this._node_child_ids_to_num_documents(node)
    for child_id in @id_tree.children[id]
      child_node = this._create_node(child_id)
      # Even if the nodes are loaded, we'll add them here as unloaded nodes,
      # then we'll switch them to loaded in later _animate_load_node() calls.
      child_node.loaded = false
      child_node.loaded_animation_fraction.current = 0
      child_node.num_documents.current = nd[child_id]
      @_nodes[child_node.id] = child_node
      node.children.push(child_node)

  _node_child_ids_to_num_documents: (node) ->
    n = @on_demand_tree.nodes

    ret = {}
    undefined_child_ids = []
    num_documents = n[node.id].doclist.n

    for child_id in @id_tree.children[node.id]
      child_num_documents = n[child_id]?.doclist?.n
      if child_num_documents?
        num_documents -= child_num_documents
        ret[child_id] = child_num_documents
      else
        undefined_child_ids.push(child_id)

    if undefined_child_ids.length
      x = num_documents / undefined_child_ids.length
      ret[child_id] = x for child_id in undefined_child_ids

    ret

  _animate_update_node_sibling_num_documents: (id, time) ->
    parent_id = @id_tree.parent[id]
    return if !parent_id? # root node

    parent_node = @_nodes[parent_id]
    nd = this._node_child_ids_to_num_documents(@_nodes[parent_id])

    for sibling_node in parent_node.children
      @animator.animate_object_properties(sibling_node, { num_documents: nd[sibling_node.id] }, undefined, time)

  _animate_remove_undefined_node: (id, time) ->
    node = @_nodes[id]
    @animator.animate_object_properties(node, { loaded_animation_fraction: 0 }, undefined, time)
    # We'll delete it from @_nodes in the parent's removal. We know the parent
    # is being unloaded.

  _animate_unload_node: (id, time) ->
    node = @_nodes[id]
    node.tagcounts = undefined
    node.loaded = false
    @animator.animate_object_properties(node, { loaded_animation_fraction: 0 }, =>
      delete @_nodes[child.id] for child in node.children
      @_nodes[id].children = []
    , time)

  _create_node: (id) ->
    n = @on_demand_tree.nodes[id]
    loaded = n?
    selected = @state.selection.nodes.indexOf(id) != -1
    {
      id: id,
      loaded: loaded,
      loaded_animation_fraction: { current: loaded && 1 || 0 },
      selected: selected,
      selected_animation_fraction: { current: selected && 1 || 0 },
      num_documents: { current: n?.doclist?.n },
      tagcounts: n?.tagcounts
      children: [],
    }

exports = require.make_export_object('models/animated_tree')
exports.AnimatedTree = AnimatedTree
