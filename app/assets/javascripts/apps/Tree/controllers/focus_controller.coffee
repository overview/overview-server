define [ 'underscore', '../views/FocusView', './logger' ], (_, FocusView, Logger) ->
  log = Logger.for_component('focus')

  throttled_log = _.throttle(log, 500)

  focus_controller = (div, focus) ->
    view = new FocusView({ el: div, model: focus })

    view.on 'zoom-pan', (obj) ->
      throttled_log('zoomed/panned', "zoom #{obj.zoom}, pan #{obj.pan}")
      focus.setPanAndZoom(obj.pan, obj.zoom)
