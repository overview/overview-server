class Router
  constructor: (start_path=undefined) ->
    # TODO: test, etc
    @start_path = start_path || '' + window.document.location
    if match = /(\d+)[^\d]*$/.exec(@start_path)
      @document_set_id = +match[0]

  route_to_path: (route) ->
    switch (route)
      when 'root' then this._root_path()
      when 'documents' then this._documents_path(this._tree_id)

  _root_path: () ->
    "/tree/#{@document_set_id}/root"

  _documents_path: () ->
    "/tree/#{@document_set_id}/documents"

exports = require.make_export_object('models/router')
exports.Router = Router
