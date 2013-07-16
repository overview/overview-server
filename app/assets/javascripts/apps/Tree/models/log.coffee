define [ 'jquery' ], ($) ->
  class Log
    constructor: () ->
      @entries = []

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
          url: "#{window.location.pathname}/log-entries/create-many?#{window.csrfTokenQueryString || ''}"
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
