DocumentList = require('models/document_list').DocumentList

node_form_controller = require('controllers/node_form_controller').node_form_controller
NodeFormView = require('views/node_form_view').NodeFormView
ListSelection = require('models/list_selection').ListSelection
DocumentListView = require('views/document_list_view').DocumentListView
log = require('globals').logger.for_component('document_list')

DOCUMENT_LIST_REQUEST_SIZE = 20

VIEW_OPTIONS = {
  buffer_documents: 5,
}

tag_to_short_string = (tag) ->
  "#{tag.id} (#{tag.name})"

node_to_short_string = (node) ->
  "#{node.id} (#{node.description})"

node_diff_to_string = (node1, node2) ->
  changed = false
  s = ''
  if node1.description != node2.description
    s += " description: <<#{node1.description}>> to <<#{node2.description}>>"
  if !changed
    s += ' (no change)'
  s

is_mac_os = () -> /Mac/.test(navigator.platform)

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

  view.observe 'edit-node', (nodeid) ->
    node = cache.on_demand_tree.nodes[nodeid]
    log('began editing node', node_to_short_string(node))
    node_form_controller(node, cache, state)

  view.observe 'edit-tag', (tagid) ->
    tag = cache.tag_store.find_tag_by_id(tagid)
    log('clicked edit tag', tag_to_short_string(tag))
    tag_form_controller(tag, cache, state)

  select_index = (index, options) ->
    if index == -1
      selected_indices.unset() if !options.meta && !options.shift
      index = 0
    else if index >= document_list.documents.length
      return # the document isn't loaded
    else
      # Update selection, taking modifier keys and platform into account
      if !options.meta && !options.shift
        selected_indices.set_index(index)
      else if is_mac_os()
        # Mac OS: Command key overrides Shift key
        if options.meta # (Shift+Command or Command)
          selected_indices.add_or_remove_index(index)
        else # (Shift only)
          selected_indices.set_range_from_last_index_to_index(index)
      else
        # Windows/Linux: Shift+Command adds ranges
        if options.shift
          if options.meta # (Shift+Ctrl)
            selected_indices.add_or_expand_range_from_last_index_to_index(index)
          else # (Shift only)
            selected_indices.set_range_from_last_index_to_index(index)
        else # (Ctrl only)
          selected_indices.add_or_remove_index(index)

    view.set_cursor_index(index)

    selected_docids = selected_indices.get_indices().map((i) -> document_list.documents[i].id)
    state.set('selection', listed_selection.replace({ documents: selected_docids }))

  click_docid = (docid, options) ->
    log('clicked document', "#{docid} meta:#{options.meta} shift: #{options.shift}")
    index = _(document_list.documents).pluck('id').indexOf(docid)
    select_index(index, options)

  go_up_or_down = (up_or_down, event) ->
    options = {
      meta: event.ctrlKey || event.metaKey || false
      shift: event.shiftKey || false
    }
    diff = up_or_down == 'down' && 1 || -1
    new_index = view.cursor_index + diff

    docid = 0 <= new_index < document_list.documents.length && document_list.documents[new_index] || undefined

    log("went #{up_or_down}", "docid:#{docid} index:#{new_index} meta:#{options.meta} shift: #{options.shift }")

    select_index(new_index, options)

  go_down = (event) -> go_up_or_down('down', event)
  go_up = (event) -> go_up_or_down('up', event)
  select_all = (event) -> select_index(-1, { meta: false, shift: false })

  view.observe('document-clicked', click_docid)

  {
    go_up: go_up
    go_down: go_down
    select_all: select_all
  }

exports = require.make_export_object('controllers/document_list_controller')
exports.document_list_controller = document_list_controller
