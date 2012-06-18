$ = jQuery

Router = require('models/router').Router

class Server
  constructor: (router=undefined) ->
    @router = router || new Router()

  get: (route, options=undefined) ->
    path = @router.route_to_path(route)

    $.ajax($.extend({
      dataType: 'json',
    }, options || {}, {
      type: 'GET',
      url: path,
    }))

exports = require.make_export_object('models/server')
exports.Server = Server
