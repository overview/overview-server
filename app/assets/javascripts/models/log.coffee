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

  upload_entries_to_server_and_clear: (server) ->
    return if @entries.length == 0

    server.post('create_log_entries', JSON.stringify(@entries), {
      contentType: 'application/json',
    })
    this.clear_entries()

exports = require.make_export_object('models/log')
exports.Log = Log
