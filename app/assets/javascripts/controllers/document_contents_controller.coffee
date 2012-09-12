DocumentContentsView = require('views/document_contents_view').DocumentContentsView

document_contents_controller = (div, cache, state) ->
  view = new DocumentContentsView(div, cache, state)

exports = require.make_export_object('controllers/document_contents_controller')
exports.document_contents_controller = document_contents_controller
