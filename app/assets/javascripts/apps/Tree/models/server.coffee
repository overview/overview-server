define [ 'jquery', './router' ], ($, Router) ->
  class Server
    constructor: (router=undefined) ->
      @router = router || new Router()

    get: (route, options=undefined) ->
      path = @router.route_to_path(route, options?.path_argument)

      $.ajax($.extend({
        dataType: 'json',
      }, options || {}, {
        type: 'GET'
        url: path
        cache: false
      }))

    post: (route, data, options=undefined) ->
      path = @router.route_to_path(route, options?.path_argument)

      $.ajax($.extend({
        data: $.extend({}, window.csrfTokenData || {}, data)
      }, options || {}, {
        type: 'POST'
        url: path,
      }))

    delete: (route, data, options=undefined) ->
      path = @router.route_to_path(route, options?.path_argument)

      $.ajax($.extend({
        data: $.extend({}, window.csrfTokenData || {}, data)
      }, options || {}, {
        type: 'DELETE'
        url: path
      }))
