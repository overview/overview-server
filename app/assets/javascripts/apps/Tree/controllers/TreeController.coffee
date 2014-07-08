define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class TreeController
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.log, a Function' if !options.log
      throw 'Must pass options.focus, a Focus' if !options.focus
      throw 'Must pass options.tree, an OnDemandTree' if !options.tree
      throw 'Must pass options.view, a TreeView' if !options.view

      @state = options.state
      @log = options.log
      @focus = options.focus
      @tree = options.tree
      @view = options.view
      @requestAnimationFrame = (options.requestAnimationFrame || window.requestAnimationFrame).bind(window)

      @_attach()
      @_init()

    _attach: ->
      @listenTo(@view, 'needs-update', @_animate)
      @listenTo(@view, 'click', @_onClick)
      @listenTo(@view, 'expand', @_onExpand)
      @listenTo(@view, 'collapse', @_onCollapse)

    _init: ->
      @_animating = false
      @_animate()

    _animate: ->
      animate_frame = =>
        if @view.needsUpdate()
          @view.update()
          @requestAnimationFrame(animate_frame)
        else
          @_animating = false

      if !@_animating
        @_animating = true
        @requestAnimationFrame(animate_frame)

    _onClick: (maybeNode) ->
      return if !maybeNode?
      node = maybeNode
      @log('clicked node', node.id)
      @tree.demandNode(node.id)

      oldParams = @state.get('documentListParams')
      newParams = @state.get('documentListParams').reset.byNode(node)
      if newParams.equals(oldParams)
        @state.set
          document: null
          oneDocumentSelected: false
      else
        @state.set
          document: null
          documentListParams: newParams

    _onExpand: (node) ->
      @log('expanded node', node.id)
      @tree.demandNode(node.id)

    _onCollapse: (node) ->
      @log('collapsed node', node.id)
      @tree.unloadNodeChildren(node.id)

      params = @state.get('documentListParams')
      selectedNodeId = params?.node?.id
      if selectedNodeId && @tree.id_tree.is_id_ancestor_of_id(node.id, selectedNodeId)
        @state.set
          document: null
          documentListParams: params.reset.byNode(node)

    _selectNode: (node) ->
      @state.resetDocumentListParams().byNode(node)
      @tree.demandNode(node.id)

    _findNodeRelativeToSelectedNode: (finder) ->
      nodeId = @state.get('documentListParams')?.node?.id || null
      return @tree.getRoot() if !nodeId?
      newId = @view[finder](nodeId)
      if newId?
        @tree.getNode(newId)
      else
        null

    _go: (finder) ->
      newNode = @_findNodeRelativeToSelectedNode(finder)
      @_selectNode(newNode) if newNode?

    goUp: -> @_go('nodeid_above')
    goDown: -> @_go('nodeid_below')
    goLeft: -> @_go('nodeid_left')
    goRight: -> @_go('nodeid_right')

  #  # Returns a Promise which will resolve when the node is expanded.
  #  #
  #  # The Promise will be returned resolved if the node is already expanded
  #  # or is a leaf.
  #  expand_promise = (node) ->
  #    throw new Error("Must pass a valid Node") if !node.id?
  #    children = onDemandTree.id_tree.children[node.id]
  #    if !children?
  #      onDemandTree.demand_node(node.id)
  #        .then (json) ->
  #          nodeIds = _.pluck(json?.nodes || [], 'id')
  #          if nodeIds.length
  #            taglikeCid = state.get('taglikeCid')
  #            onDemandTree.refreshTaglikeCounts(taglikeCid, nodeIds)
  #    else
  #      $.Deferred().resolve()

  #  # Returns a Promise which will resolve when the node is expanded.
  #  #
  #  # Side-effects: logs the action.
  #  #
  #  # The Promise will be returned resolved if the node is already expanded
  #  # or is a leaf.
  #  expand_promise_with_log = (node) ->
  #    promise = expand_promise(node)
  #    if promise.state?() == 'resolved'
  #      log('attempt to expand already-expanded or leaf node', "#{node.id}")
  #    else
  #      log('expand node', "#{node.id}")
  #    promise

  #  expand = (e) ->
  #    node = selected_node()
  #    expand_promise_with_log(node)

  #  collapse = (e) ->
  #    node = selected_node()
  #    log('attempt to collapse', "#{node.id}")

  #  toggle_expand = (e) ->
  #    node = selected_node()
  #    child_nodeid = view.nodeid_below(node.id)
  #    if !child_nodeid && onDemandTree.id_tree.children[node.id]?.length
  #      log('toggling node to expanded', "#{node.id}")
  #      expand_promise(node)
  #    else
  #      log('toggling node to collapsed', "#{node.id}")
  #      # FIXME actually collapse

  #  go_up = (e) -> go('nodeid_above', e)
  #  go_left = (e) -> go('nodeid_left', e)
  #  go_right = (e) -> go('nodeid_right', e)

  #  go_down = (e) ->
  #    node = selected_node()
  #    promise = expand_promise(node)
  #    if promise.state?() == 'resolved'
  #      go('nodeid_below', e)
  #    else
  #      promise.then ->
  #        console.log('THEN')
  #        # We can't query the view for a DrawableNode, because the view hasn't
  #        # drawn yet. However, we know the view *will* draw the added children,
  #        # so we can select one anyway.
  #        child_nodeid = onDemandTree.id_tree.children[node.id]?[0]
  #        if child_nodeid
  #          log('expanded and moved to nodeid_below', "nodeid_before:#{node.id} nodeid_after:#{child_nodeid}")
  #          child_node = onDemandTree.getNode(child_nodeid)
  #          select_node(child_node)

  #  {
  #    go_up: go_up
  #    go_down: go_down
  #    go_left: go_left
  #    go_right: go_right
  #    expand: expand
  #    collapse: collapse
  #    toggle_expand: toggle_expand
  #  }
