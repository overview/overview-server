DocumentContentsView = require('views/document_contents_view').DocumentContentsView

document_contents_controller = (div, selection, router) ->
  view = new DocumentContentsView(div, selection, router)

exports = require.make_export_object('controllers/document_contents_controller')
exports.document_contents_controller = document_contents_controller
