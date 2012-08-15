DocumentContentsView = require('views/document_contents_view').DocumentContentsView

document_contents_controller = (div, state, router) ->
  view = new DocumentContentsView(div, state, router)

exports = require.make_export_object('controllers/document_contents_controller')
exports.document_contents_controller = document_contents_controller
