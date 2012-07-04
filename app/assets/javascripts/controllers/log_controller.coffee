FLUSH_INTERVAL = 15000 # in ms

log_controller = (log, server) ->
  flush = log.upload_entries_to_server_and_clear.bind(log, server)

  window.setInterval(flush, FLUSH_INTERVAL)

exports = require.make_export_object('controllers/log_controller')
exports.log_controller = log_controller
