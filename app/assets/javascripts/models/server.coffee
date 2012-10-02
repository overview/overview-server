$ = jQuery

Router = require('models/router').Router

class Server
  constructor: (router=undefined) ->
    @router = router || new Router()

  get: (route, options=undefined) ->
    path = @router.route_to_path(route, options?.path_argument)

    $.ajax($.extend({
      dataType: 'json',
    }, options || {}, {
      type: 'GET',
      url: path,
    }))

  post: (route, data, options=undefined) ->
    path = @router.route_to_path(route, options?.path_argument)

    $.ajax($.extend({
      data: data,
    }, options || {}, {
      type: 'POST',
      url: path,
    }))

  delete: (route, data, options=undefined) ->
    path = @router.route_to_path(route, options?.path_argument)

    $.ajax($.extend({
      data: data,
    }, options || {}, {
      type: 'DELETE',
      url: path,
    }))

exports = require.make_export_object('models/server')
exports.Server = Server
