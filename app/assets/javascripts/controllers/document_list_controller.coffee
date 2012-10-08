DocumentList = require('models/document_list').DocumentList
ListSelection = require('models/list_selection').ListSelection
DocumentListView = require('views/document_list_view').DocumentListView
log = require('globals').logger.for_component('document_list')

DOCUMENT_LIST_REQUEST_SIZE = 20

VIEW_OPTIONS = {
  buffer_documents: 5,
}

document_list_controller = (div, cache, state) ->
  listed_selection = undefined
  document_list = undefined
  selected_indices = undefined
  view = undefined

  maybe_fetch = () ->
    need_documents = view.get_top_need_documents()
    return if !need_documents
    max = need_documents[0] + DOCUMENT_LIST_REQUEST_SIZE
    document_list.slice(need_documents[0], max)

  refresh_document_list = () ->
    document_list.destroy() if document_list?
    document_list = new DocumentList(cache, listed_selection)
    selected_indices = new ListSelection()
    if !view?
      view = new DocumentListView(div, cache, document_list, state, VIEW_OPTIONS)
    else
      view.set_document_list(document_list)
    maybe_fetch()

  maybe_update_stored_selection = () ->
    new_selection = state.selection.pick('nodes', 'tags')
    return if _.isEqual(new_selection, listed_selection)
    listed_selection = new_selection
    refresh_document_list()

  maybe_update_stored_selection() # sets view

  state.observe('selection-changed', maybe_update_stored_selection)

  view.observe('need-documents', maybe_fetch)

  view.observe 'document-clicked', ->
    docid = view.last_document_id_clicked()
    index = _(document_list.documents).pluck('id').indexOf(docid)
    log('clicked document', "#{docid}")

    selected_indices.set_index(index)
    selected_docids = selected_indices.get_indices().map((i) -> document_list.documents[i].id)

    state.set('selection', listed_selection.plus({ documents: selected_docids }))

exports = require.make_export_object('controllers/document_list_controller')
exports.document_list_controller = document_list_controller
