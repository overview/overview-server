define [
  'underscore'
  'jquery'
  '../models/AnimatedTree'
  '../models/animator'
  '../models/property_interpolator'
  '../models/TreeLayout'
  '../views/TreeView'
  './logger'
], (_, $, AnimatedTree, Animator, PropertyInterpolator, TreeLayout, TreeView, Logger) ->
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
    layout = new TreeLayout(animator)
    animated_tree = new AnimatedTree(cache.on_demand_tree, state, animator, layout)
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

    view.observe('needs-update', animate)

    view.observe 'click', (nodeid) ->
      return if !nodeid?
      log('clicked node', "#{nodeid}")
      expand_deferred(nodeid)
      new_selection = state.get('selection').replace({ nodes: [nodeid], tags: [], documents: [], searchResults: [] })
      state.set('selection', new_selection)

    view.observe 'expand', (nodeid) ->
      return if !nodeid?
      log('expanded node', "#{nodeid}")
      expand_deferred(nodeid)

    view.observe 'collapse', (nodeid) ->
      return if !nodeid?
      log('collapsed node', "#{nodeid}")
      new_selection = update_selection_to_parent_of_nodeid_if_necessary(state.get('selection'), nodeid, cache.on_demand_tree)
      state.set('selection', new_selection)
      cache.on_demand_tree.unload_node_children(nodeid)

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

    state.on 'change:selection', (__, selection) ->
      if nodeid = selection.nodes[0]
        node = animated_tree.getAnimatedNode(nodeid)
        if node.parent?
          if node.size.width < node.parent.size.width / 4
            node.narrow = true
            focus.animateNode(node)
          else
            focus.animateNode(node.parent) # this is the most common case.
        else
          focus.animateNode(node) # root node.

    state.on 'change:taglike', (__, taglike) ->
      if taglike?.name? # it's a tag
        cache.refresh_tagcounts(taglike)
      else if taglike?.id > 0 && taglike?.query? # it's a search result
        cache.refreshSearchResultCounts(taglike)

    select_nodeid = (nodeid) ->
      new_selection = state.get('selection').replace({ nodes: [nodeid], tags: [], documents: [], searchResults: [] })
      state.set('selection', new_selection)

    selected_nodeid = ->
      state.get('selection').nodes[0] || cache.on_demand_tree.id_tree.root

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
        expand_deferred(new_nodeid)
      else
        log("failed to move to #{finder}", "nodeid_before:#{nodeid}")
      new_nodeid

    # Returns a jQuery Deferred which will resolve when the node is expanded.
    #
    # The Deferred will be returned resolved if the node is already expanded
    # or is a leaf.
    expand_deferred = (nodeid) ->
      children = cache.on_demand_tree.id_tree.children[nodeid]
      if !children?
        cache.on_demand_tree.demand_node(nodeid)
          .done (json) ->
            taglike = state.get('taglike')
            if taglike?
              nodeIds = _.pluck(json?.nodes || [], 'id')
              if nodeIds.length
                if taglike.name? # it's a tag
                  cache.refresh_tagcounts(taglike, nodeIds)
                else if taglike.query? # it's a searchResult
                  cache.refreshSearchResultCounts(taglike, nodeIds)
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
