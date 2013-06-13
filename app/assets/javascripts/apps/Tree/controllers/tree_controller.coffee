define [
  'underscore'
  'jquery'
  '../models/animated_tree'
  '../models/animator'
  '../models/property_interpolator'
  '../views/tree_view'
  './logger'
], (_, $, AnimatedTree, Animator, PropertyInterpolator, TreeView, Logger) ->
  log = Logger.for_component('tree')

  log_pan_zoom = _.throttle(((args...) -> log('zoomed/panned', args...)), 500)

  update_selection_to_parent_of_nodeid_if_necessary = (selection, nodeid, on_demand_tree) ->
    to_remove = []

    for maybe_child_nodeid in selection.nodes
      if on_demand_tree.id_tree.is_id_ancestor_of_id(nodeid, maybe_child_nodeid)
        to_remove.push(maybe_child_nodeid)

    if to_remove.length
      selection.minus({ nodes: to_remove }).plus({ nodes: [ nodeid ] })
    else
      selection

  # Shim window.requestAnimationFrame(), as per
  # http://my.opera.com/emoller/blog/2011/12/20/requestanimationframe-for-smart-er-animating
  (->
    last_time = 0
    vendors = [ 'ms', 'moz', 'webkit', 'o' ]
    for vendor in vendors
      break if window.requestAnimationFrame
      window.requestAnimationFrame = window["#{vendor}RequestAnimationFrame"]

    if !window.requestAnimationFrame
      window.requestAnimationFrame = (callback) ->
        cur_time = new Date().getTime()
        time_to_call = Math.max(0, 16 - (cur_time - last_time))
        id = window.setTimeout((-> callback(cur_time + time_to_call)), time_to_call)
        last_time = cur_time + time_to_call
        id
  )()

  tree_controller = (div, cache, focus, state) ->
    interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
    animator = new Animator(interpolator)
    animated_tree = new AnimatedTree(cache.on_demand_tree, state, animator)
    view = new TreeView(div, cache, animated_tree, focus)

    animating = false
    animate_frame = () ->
      if view.needs_update()
        view.update()
        requestAnimationFrame(animate_frame)
      else
        animating = false
    animate = () ->
      if !animating
        animating = true
        requestAnimationFrame(animate_frame)

    # XXX maybe move the focus tag into AnimatedTree?
    state.observe('focused_tag-changed', -> cache.on_demand_tree.id_tree.edit(->))

    view.observe('needs-update', animate)

    view.observe 'click', (nodeid) ->
      return if !nodeid?
      log('clicked node', "#{nodeid}")
      new_selection = state.selection.replace({ nodes: [nodeid], tags: [], documents: [], searchResults: [] })

      node = cache.on_demand_tree.nodes[nodeid]
      if node?.doclist?.n == 1
        new_selection = new_selection.replace({ documents: [ node.doclist.docids[0] ] })

      state.set('selection', new_selection)

    view.observe 'expand', (nodeid) ->
      return if !nodeid?
      log('expanded node', "#{nodeid}")
      cache.on_demand_tree.demand_node(nodeid)

    view.observe 'collapse', (nodeid) ->
      return if !nodeid?
      log('collapsed node', "#{nodeid}")
      new_selection = update_selection_to_parent_of_nodeid_if_necessary(state.selection, nodeid, cache.on_demand_tree)
      state.set('selection', new_selection)
      cache.on_demand_tree.unload_node_children(nodeid)

    view.observe 'zoom-pan', (obj) ->
      log_pan_zoom("zoom #{obj.zoom}, pan #{obj.pan}")
      focus.set_auto_pan_zoom(false)
      focus.set_zoom(obj.zoom)
      focus.set_pan(obj.pan)

    state.observe 'selection-changed', ->
      if nodeid = selected_nodeid()
        expand(nodeid)
        
    select_nodeid = (nodeid) ->
      new_selection = state.selection.replace({ nodes: [nodeid], tags: [], documents: [], searchResults: [] })
      state.set('selection', new_selection)

    selected_nodeid = () ->
      state.selection.nodes[0] || cache.on_demand_tree.id_tree.root

    # Moves selection in the given direction.
    #
    # Returns the new nodeid, which may be undefined
    go = (finder, e) ->
      view.set_hover_node(undefined)
      nodeid = selected_nodeid()
      new_nodeid = view[finder](nodeid)
      if new_nodeid?
        log("moved to #{finder}", "nodeid_before:#{nodeid} nodeid_after:#{new_nodeid}")
        select_nodeid(new_nodeid)
      else
        log("failed to move to #{finder}", "nodeid_before:#{nodeid}")
      new_nodeid

    # Returns a jQuery Deferred which will resolve when the node is expanded.
    #
    # The Deferred will be returned resolved if the node is already expanded
    # or is a leaf.
    expand_deferred = (nodeid) ->
      child_nodeid = view.nodeid_below(nodeid)
      if !child_nodeid && cache.on_demand_tree.id_tree.children[nodeid]?.length
        cache.on_demand_tree.demand_node(nodeid)
      else
        $.Deferred().resolve()

    # Returns a jQuery Deferred which will resolve when the node is expanded.
    #
    # Side-effects: logs the action.
    #
    # The Deferred will be returned resolved if the node is already expanded
    # or is a leaf.
    expand_deferred_with_log = (nodeid) ->
      deferred = expand_deferred(nodeid)
      if deferred.state() == 'resolved'
        log('attempt to expand already-expanded or leaf node', "#{nodeid}")
      else
        log('expand node', "#{nodeid}")
      deferred

    expand = (e) ->
      nodeid = selected_nodeid()
      expand_deferred_with_log(nodeid)

    collapse = (e) ->
      nodeid = selected_nodeid()
      log('attempt to collapse', "#{nodeid}")

    toggle_expand = (e) ->
      nodeid = selected_nodeid()
      child_nodeid = view.nodeid_below(nodeid)
      if !child_nodeid && cache.on_demand_tree.id_tree.children[nodeid]?.length
        log('toggling node to expanded', "#{nodeid}")
        expand_deferred(nodeid)
      else
        log('toggling node to collapsed', "#{nodeid}")
        # FIXME actually collapse

    go_up = (e) -> go('nodeid_above', e)
    go_left = (e) -> go('nodeid_left', e)
    go_right = (e) -> go('nodeid_right', e)

    go_down = (e) ->
      nodeid = selected_nodeid()
      deferred = expand_deferred(nodeid)
      if deferred.state() == 'resolved'
        go('nodeid_below', e)
      else
        deferred.done ->
          # We can't query the view for a DrawableNode, because the view hasn't
          # drawn yet. However, we know the view *will* draw the added children,
          # so we can select one anyway.
          child_nodeid = cache.on_demand_tree.id_tree.children[nodeid]?[0]
          if child_nodeid
            log('expanded and moved to nodeid_below', "nodeid_before:#{nodeid} nodeid_after:#{child_nodeid}")
            select_nodeid(child_nodeid)

    {
      go_up: go_up
      go_down: go_down
      go_left: go_left
      go_right: go_right
      expand: expand
      collapse: collapse
      toggle_expand: toggle_expand
    }
