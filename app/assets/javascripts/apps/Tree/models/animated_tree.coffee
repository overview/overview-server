define [ './observable', './animated_node' ], (observable, AnimatedNode) ->
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
      @_last_selected_nodes = @state.selection.nodes
      @_needs_update = false

      this._attach()

    set_needs_update: () -> this._maybe_notifying_needs_update(->)
    needs_update: () -> @_needs_update || @animator.needs_update()

    update: () ->
      @_needs_update = false
      @animator.update()

    _attach: () ->
      @id_tree.observe 'edit', () =>
        if !@root? && @id_tree.root != -1
          this._load()
        else
          this._change()

      @state.observe('selection-changed', this._change.bind(this))

    _maybe_notifying_needs_update: (callback) ->
      old_needs_update = this.needs_update()
      callback.apply(this)
      this._notify('needs-update') if !old_needs_update && this.needs_update()

    _load: () ->
      @root = this._create_animated_node(@id_tree.root)
      this._notify('needs-update')

    _node_is_partially_loaded: (node) ->
      node?.size && @id_tree.children[node?.id] || false

    _node_is_fully_loaded: (node) ->
      return false if !this._node_is_partially_loaded(node)

      # Look at id_tree, not the node, because the node isn't the authority
      childids = @id_tree.children[node.id]
      child_nodes = childids?.map((childid) => @on_demand_tree.nodes[childid])
      child_nodes?.every((child) => this._node_is_partially_loaded(child)) || false

    _refresh_animated_node: (animated_node, selected_node_set, time) ->
      node = animated_node.node

      # Update whether it's selected
      current_selected = animated_node.selected
      new_selected = selected_node_set[node.id] || false
      if current_selected != new_selected
        animated_node.set_selected(new_selected, @animator, time)

      # Update whether it's loaded
      if !animated_node.children?
        if this._node_is_fully_loaded(node)
          animated_children = node.children.map((childid) => this._create_animated_node(childid))
          if animated_children.indexOf(undefined) == -1
            animated_node.load(animated_children, @animator, time)
      else # animated_node.children?
        if !this._node_is_fully_loaded(node)
          animated_node.unload(@animator, time)
        else
          # Recurse
          this._refresh_animated_node(child, selected_node_set, time) for child in animated_node.children

      undefined

    _change: () ->
      return if !@root?

      # Walk the entire tree, noticing differences and acting accordingly
      this._maybe_notifying_needs_update ->
        @_needs_update = true
        selected_node_set = {}
        selected_node_set[id] = true for id in @state.selection.nodes
        this._refresh_animated_node(@root, selected_node_set, Date.now())

    _create_animated_node: (id) ->
      node = @on_demand_tree.nodes[id]
      if node?
        selected = @state.selection.nodes.indexOf(id) != -1

        animated_children = node.children?.map((childid) => this._create_animated_node(childid))
        # animated_children is now undefined if node.children is.
        # It should also be undefined if it isn't complete:
        if animated_children?.indexOf(undefined) != -1
          animated_children = undefined
        
        new AnimatedNode(node, animated_children, selected)
      else
        # Undefined nodes are a recursion detail.
        # They are NEVER inserted into the tree.
        undefined
