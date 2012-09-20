DocumentList = require('models/document_list').DocumentList
DocumentListView = require('views/document_list_view').DocumentListView
log = require('globals').logger.for_component('document_list')

DOCUMENT_LIST_REQUEST_SIZE = 20

VIEW_OPTIONS = {
  buffer_documents: 5,
}

document_list_controller = (div, cache, state) ->
  listed_selection = undefined
  document_list = undefined
  view = undefined

  maybe_fetch = () ->
    need_documents = view.get_top_need_documents()
    return if !need_documents
    max = need_documents[0] + DOCUMENT_LIST_REQUEST_SIZE
    document_list.slice(need_documents[0], max)

  refresh_document_list = () ->
    document_list.destroy() if document_list?
    document_list = new DocumentList(cache, listed_selection)
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

  get_view_document = () ->
    documentid = view.last_document_id_clicked()
    cache.document_store.documents[documentid]

  state.observe('selection-changed', maybe_update_stored_selection)

  view.observe('need-documents', maybe_fetch)

  view.observe 'document-clicked', ->
    document = get_view_document()
    log('clicked document', "#{document?.id}")
    state.set('selection', listed_selection.plus({ documents: document?.id? && [document.id] || [] }))

exports = require.make_export_object('controllers/document_list_controller')
exports.document_list_controller = document_list_controller
