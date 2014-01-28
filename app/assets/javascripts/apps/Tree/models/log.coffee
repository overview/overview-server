define [ 'jquery' ], ($) ->
  class Log
    constructor: () ->
      @entries = []
      if match = /documentsets\/(\d+)\/.*$/.exec(window.location.pathname)
        @document_set_id = +match[1]
        @base_url = "/documentsets/#{@document_set_id}"
        
    add_entry: (entry) ->
      @entries.push({
        date: (new Date()).toISOString(),
        component: entry.component || ''
        action: entry.action || ''
        details: entry.details || ''
      })

    clear_entries: () ->
      @entries = []

    upload_entries_to_server_and_clear: () ->
      if @entries.length > 0
        data = JSON.stringify(@entries)

        this.clear_entries()

        $.ajax({
          url: "#{@base_url}/log-entries/create-many?#{window.csrfTokenQueryString || ''}"
          type: 'POST'
          data: data
          contentType: 'application/json'
          global: false
        })
      else
        $.Deferred().resolve()

    for_component: (component) ->
      (action, details=undefined) =>
        this.add_entry({ component: component, action: action, details: details })
