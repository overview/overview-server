class Router
  constructor: (start_path=undefined) ->
    # TODO: test, etc
    @start_path = start_path || '' + window.document.location

  route_to_path: (route) ->
    this._root_path()

  _root_path: () ->
    "#{@start_path}/tree/root"

exports = require.make_export_object('models/router')
exports.Router = Router
