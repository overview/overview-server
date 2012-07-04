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

exports = require.make_export_object('models/log')
exports.Log = Log
