DocumentList = require('models/document_list').DocumentList
DocumentListView = require('views/document_list_view').DocumentListView
log = require('globals').logger.for_component('document_list')

DOCUMENT_LIST_REQUEST_SIZE = 20

# We use a "stored_selection" concept here. It's like a Selection but it's
# compact, easy to test for equality, and it doesn't take documents into
# account. That's because we want our DocumentListView not to filter by
# selected documents--we're using the DocumentListView to *select* documents,
# so we need to show the ones that aren't selected.
array_to_ids = (a) ->
  (x.id? && x.id || x for x in a)

selection_to_stored_selection = (s) ->
  {
    nodes: array_to_ids(s.nodes),
    tags: array_to_ids(s.tags),
    documents: []
  }

VIEW_OPTIONS = {
  buffer_documents: 5,
}

document_list_controller = (div, store, resolver, selection) ->
  stored_selection = undefined
  document_list = undefined
  view = undefined

  maybe_fetch = () ->
    need_documents = view.get_top_need_documents()
    return if !need_documents
    max = need_documents[0] + DOCUMENT_LIST_REQUEST_SIZE
    document_list.slice(need_documents[0], max)

  refresh_document_list = () ->
    document_list = new DocumentList(store, stored_selection, resolver)
    if !view?
      view = new DocumentListView(div, document_list, selection, VIEW_OPTIONS)
    else
      view.set_document_list(document_list)
    maybe_fetch()

  maybe_update_stored_selection = () ->
    new_stored_selection = selection_to_stored_selection(selection)
    return if _.isEqual(stored_selection, new_stored_selection)
    stored_selection = new_stored_selection
    refresh_document_list()

  maybe_update_stored_selection() # sets view

  get_view_document = () ->
    documentid = view.last_document_id_clicked()
    store.documents.get(documentid)

  selection.observe(maybe_update_stored_selection)

  view.observe('need-documents', maybe_fetch)

  view.observe 'document-clicked', ->
    document = get_view_document()
    log('clicked document', "#{document?.id}")
    selection.update({ document: document })

exports = require.make_export_object('controllers/document_list_controller')
exports.document_list_controller = document_list_controller
