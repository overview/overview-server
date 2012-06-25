DocumentList = require('models').DocumentList
document_list_view = require('views').document_list_view

document_list_controller = (div, store, resolver, state) ->
  document_list = new DocumentList(store, state.selection, resolver)

  view = document_list_view(div, document_list)

  state.selection.observe ->
    document_list = new DocumentList(store, this, resolver)
    view.set_document_list(document_list)

  view.on 'need_more_documents', (start, end) ->
    document_list.slice(start, end).done (documents) ->
      view.add_slice(start, end, documents)

  view.on 'document_clicked', (document_id) ->
    console.log("Clicked document: ID #{document_id}")

  view.on 'unloaded_document_clicked', (index) ->
    console.log("Unloaded document clicked: index #{index}")

exports = require.make_export_object('controllers/document_list_controller')
exports.document_list_controller = document_list_controller
