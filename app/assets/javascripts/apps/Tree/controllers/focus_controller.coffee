define [ 'underscore', '../views/focus_view', './logger' ], (_, FocusView, Logger) ->
  log = Logger.for_component('focus')

  throttled_log = _.throttle(log, 500)

  focus_controller = (div, focus) ->
    view = new FocusView(div, focus)

    view.observe 'zoom-pan', (obj) ->
      throttled_log('zoomed/panned', "zoom #{obj.zoom}, pan #{obj.pan}")
      focus.set_auto_pan_zoom(false)
      focus.set_zoom(obj.zoom)
      focus.set_pan(obj.pan)
