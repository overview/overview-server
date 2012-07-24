FocusView = require('views/focus_view').FocusView
log = require('globals').logger.for_component('focus')

focus_controller = (div, focus) ->
  view = new FocusView(div, focus)

  view.observe 'zoom-pan', (obj) ->
    log('zoomed/panned', "zoom #{obj.zoom}, pan #{obj.pan}")
    focus.set_zoom(obj.zoom)
    focus.set_pan(obj.pan)

exports = require.make_export_object('controllers/focus_controller')
exports.focus_controller = focus_controller
