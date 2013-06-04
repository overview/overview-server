define ->
  class Router
    constructor: (start_path=undefined) ->
      # TODO: test, etc
      @start_path = start_path || '' + window.document.location
      if match = /(\d+)[^\d]*$/.exec(@start_path)
        @document_set_id = +match[0]

    route_to_path: (route, id=undefined) ->
      switch (route)
        when 'root' then this._root_path()
        when 'node' then this._node_path(id)
        when 'node_update' then this._node_path(id)
        when 'documents' then this._documents_path()
        when 'document_view' then this._document_view_path(id)
        when 'create_log_entries' then this._create_log_entries_path()
        when 'tag_add' then this._tag_add_path(id)
        when 'tag_remove' then this._tag_remove_path(id)
        when 'tag_node_counts' then this._tag_node_counts_path(id)

    _document_view_path: (id) ->
      "/documents/#{id}"

    _root_path: () ->
      "/documentsets/#{@document_set_id}/tree/nodes"

    _node_path: (id) ->
      "/documentsets/#{@document_set_id}/tree/nodes/#{id}"

    _documents_path: () ->
      "/documentsets/#{@document_set_id}/documents"

    _create_log_entries_path: () ->
      "/documentsets/#{@document_set_id}/log-entries/create-many"

    _tag_add_path: (id) ->
      "/documentsets/#{@document_set_id}/tags/#{id}/add"

    _tag_remove_path: (id) ->
      "/documentsets/#{@document_set_id}/tags/#{id}/remove"

    _tag_node_counts_path: (id) ->
      "/documentsets/#{@document_set_id}/tags/#{id}/node-counts"
