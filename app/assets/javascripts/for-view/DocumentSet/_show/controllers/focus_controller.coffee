define [ '../views/focus_view', './logger' ], (FocusView, Logger) ->
  log = Logger.for_component('focus')

  focus_controller = (div, focus) ->
    view = new FocusView(div, focus)

    view.observe 'zoom-pan', (obj) ->
      log('zoomed/panned', "zoom #{obj.zoom}, pan #{obj.pan}")
      focus.set_auto_pan_zoom(false)
      focus.set_zoom(obj.zoom)
      focus.set_pan(obj.pan)
