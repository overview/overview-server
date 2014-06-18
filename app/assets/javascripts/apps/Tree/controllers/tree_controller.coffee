define [
  'underscore'
  'jquery'
  '../models/AnimatedTree'
  '../models/TreeLayout'
  '../views/TreeView'
  './logger'
], (_, $, AnimatedTree, TreeLayout, TreeView, Logger) ->
  log = Logger.for_component('tree')

  log_pan_zoom = _.throttle(((args...) -> log('zoomed/panned', args...)), 500)

  # If doclist is showing a descendent of node, change doclist to the
  # parent of node
  moveDocumentListParamsUpToNodeIfNecessary = (state, node, onDemandTree) ->
    params = state.get('documentListParams')
    if params.type == 'node' && onDemandTree.id_tree.is_id_ancestor_of_id(node.id, params.node.id)
      state.resetDocumentListParams().byNode(node)

  animateFocusToNode = (animated_tree, focus, node) ->
    if node.parent?
      if node.size.width < node.parent.size.width / 4
        node.narrow = true
        focus.animateNode(node)
      else
        focus.animateNode(node.parent) # this is the most common case.
    else
      focus.animateNode(node) # root node.

  tree_controller = (div, documentSet, onDemandTree, focus, state, animator) ->
    layout = new TreeLayout(animator)
    animated_tree = new AnimatedTree(onDemandTree, state, animator, layout)
    view = new TreeView(div, documentSet, animated_tree, focus)

    animating = false
    animate_frame = ->
      if view.needs_update()
        view.update()
        requestAnimationFrame(animate_frame)
      else
        animating = false
    animate = ->
      if !animating
        animating = true
        requestAnimationFrame(animate_frame)

    view.observe('needs-update', animate)
    animate()

    view.observe 'click', (node) ->
      return if !node?
      log('clicked node', "#{node.id}")
      expand_deferred(node)

      params = state.get('documentListParams').reset.byNode(node)
      if params.equals(state.get('documentListParams'))
        # Click on already-selected node -> deselect document
        state.set(oneDocumentSelected: false)
      else
        # Click on node -> select node
        state.setDocumentListParams(params)

    view.observe 'expand', (node) ->
      return if !node?
      log('expanded node', "#{node.id}")
      expand_deferred(node)

    view.observe 'collapse', (node) ->
      return if !node?
      log('collapsed node', "#{node.id}")
      moveDocumentListParamsUpToNodeIfNecessary(state, node, onDemandTree)
      onDemandTree.unload_node_children(node.id)

    view.observe 'zoom-pan', (obj, options) ->
      log_pan_zoom("zoom #{obj.zoom}, pan #{obj.pan}")

      # don't attempt to zoom past 1, since that doesn't make any sense
      if obj.zoom >= 1
        obj.zoom = 1
        obj.pan = 0

      if options?.animate
        focus.animatePanAndZoom(obj.pan, obj.zoom)
      else
        focus.setPanAndZoom(obj.pan, obj.zoom)

    state.on 'change:documentListParams', (__, params) ->
      if params.type == 'node'
        node = animated_tree.getAnimatedNode(params.node.id)
        animateFocusToNode(animated_tree, focus, node)

    select_node = (node) ->
      state.resetDocumentListParams().byNode(node)

    selected_node = ->
      state.get('documentListParams')?.node || onDemandTree.getRoot()

    # Moves selection in the given direction.
    #
    # Returns the new nodeid, which may be undefined
    go = (finder, e) ->
      view.set_hover_node(undefined)
      nodeId = selected_node()?.id
      return if !nodeId
      newNodeId = view[finder](nodeId)
      if newNodeId?
        newNode = onDemandTree.getNode(newNodeId)
        log("moved to #{finder}", "nodeid_before:#{nodeId} nodeid_after:#{newNodeId}")
        select_node(newNode)
        expand_deferred(newNode)
      else
        log("failed to move to #{finder}", "nodeid_before:#{nodeId}")
      newNode

    # Returns a jQuery Deferred which will resolve when the node is expanded.
    #
    # The Deferred will be returned resolved if the node is already expanded
    # or is a leaf.
    expand_deferred = (node) ->
      throw new Error("Must pass a valid Node") if !node.id?
      children = onDemandTree.id_tree.children[node.id]
      if !children?
        onDemandTree.demand_node(node.id)
          .done (json) ->
            nodeIds = _.pluck(json?.nodes || [], 'id')
            if nodeIds.length
              taglikeCid = state.get('taglikeCid')
              onDemandTree.refreshTaglikeCounts(taglikeCid, nodeIds)
      else
        $.Deferred().resolve()

    # Returns a jQuery Deferred which will resolve when the node is expanded.
    #
    # Side-effects: logs the action.
    #
    # The Deferred will be returned resolved if the node is already expanded
    # or is a leaf.
    expand_deferred_with_log = (node) ->
      deferred = expand_deferred(node)
      if deferred.state() == 'resolved'
        log('attempt to expand already-expanded or leaf node', "#{node.id}")
      else
        log('expand node', "#{node.id}")
      deferred

    expand = (e) ->
      node = selected_node()
      expand_deferred_with_log(node)

    collapse = (e) ->
      node = selected_node()
      log('attempt to collapse', "#{node.id}")

    toggle_expand = (e) ->
      node = selected_node()
      child_nodeid = view.nodeid_below(node.id)
      if !child_nodeid && onDemandTree.id_tree.children[node.id]?.length
        log('toggling node to expanded', "#{node.id}")
        expand_deferred(node)
      else
        log('toggling node to collapsed', "#{node.id}")
        # FIXME actually collapse

    go_up = (e) -> go('nodeid_above', e)
    go_left = (e) -> go('nodeid_left', e)
    go_right = (e) -> go('nodeid_right', e)

    go_down = (e) ->
      node = selected_node()
      deferred = expand_deferred(node)
      if deferred.state() == 'resolved'
        go('nodeid_below', e)
      else
        deferred.done ->
          # We can't query the view for a DrawableNode, because the view hasn't
          # drawn yet. However, we know the view *will* draw the added children,
          # so we can select one anyway.
          child_nodeid = onDemandTree.id_tree.children[node.id]?[0]
          if child_nodeid
            log('expanded and moved to nodeid_below', "nodeid_before:#{node.id} nodeid_after:#{child_nodeid}")
            child_node = onDemandTree.getNode(child_nodeid)
            select_node(child_node)

    {
      go_up: go_up
      go_down: go_down
      go_left: go_left
      go_right: go_right
      expand: expand
      collapse: collapse
      toggle_expand: toggle_expand
    }
