define ->
  auto_focus_controller = (focus, world) ->
    world.state.observe 'selection-changed', ->
      # Auto-focus when the user changes the selection. This will make the view
      # pan to ensure the new selection is in view.
      focus.set_auto_pan_zoom(true)

    world.cache.on_demand_tree.id_tree.observe 'edit', ->
      # Auto-focus when a node expands. This will make the view pan/zoom to keep
      # the selected node in view, even during animation.
      focus.set_auto_pan_zoom(true)
