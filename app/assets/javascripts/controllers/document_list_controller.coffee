DocumentList = require('models/document_list').DocumentList
DocumentListView = require('views/document_list_view').DocumentListView

DOCUMENT_LIST_REQUEST_SIZE = 20

# We use a "stored_selection" concept here. It's like a Selection but it's
# compact, easy to test for equality, and it doesn't take documents into
# account. That's because we want our DocumentListView not to filter by
# selected documents--we're using the DocumentListView to *select* documents,
# so we need to show the ones that aren't selected.
array_to_ids = (a) ->
  (x.id? && x.id || x for x in a)

selection_array_equals = (a, b) ->
  a = array_to_ids(a)
  a.sort()
  b = array_to_ids(b)
  b.sort()

  a.join(',') == b.join(',')

stored_selection_equals = (a, b) ->
  selection_array_equals(a.tags, b.tags) && selection_array_equals(a.nodes, b.nodes)

selection_to_stored_selection = (s) ->
  {
    nodes: array_to_ids(s.nodes),
    tags: array_to_ids(s.tags),
    documents: []
  }

document_list_controller = (div, store, resolver, state) ->
  stored_selection = selection_to_stored_selection(state.selection)
  document_list = new DocumentList(store, stored_selection, resolver)
  view = new DocumentListView(div, document_list, state.selection)

  fetch = () ->
    all_needs = view.need_documents
    return if !all_needs.length
    needs = all_needs[0]
    start = needs[0]
    end = start + DOCUMENT_LIST_REQUEST_SIZE
    if needs[1]? && needs[1] < end
      end = needs[1]
    document_list.slice(start, end)

  state.selection.observe ->
    new_stored_selection = selection_to_stored_selection(state.selection)
    return if stored_selection_equals(stored_selection, new_stored_selection)
    stored_selection = new_stored_selection
    document_list = new DocumentList(store, stored_selection, resolver)
    view.set_document_list(document_list)
    fetch()

  view.observe('need-documents', fetch)

exports = require.make_export_object('controllers/document_list_controller')
exports.document_list_controller = document_list_controller
