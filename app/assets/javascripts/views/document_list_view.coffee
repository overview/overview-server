$ = jQuery

DOCUMENT_LIST_REQUEST_SIZE = 20 # number of documents per request

document_list_view = (div, document_list) ->
  # TODO: DocumentList only depends on tags/nodes, *not* documents.
  showing_placeholders = undefined

  document_to_dom_node = (document) ->
    $a = $('<a></a>')
    $a.text(title)
    $a.attr('href', "#document-#{document.id}")

  undefined_to_dom_node = (index) ->
    $a = $('<a></a>')
    $a.text('(loading...)')
    $a.attr('href', "#loading-document-#{index}")

  create_li = (document_or_index) ->
    $li = $('<li></li>')
    $node = if document_or_index.id?
      document_to_dom_node(document_or_index)
    else
      showing_placeholders = true
      $li.addClass('placeholder')
      undefined_to_dom_node(document_or_index)
    $li.append($node)

  redraw = () ->
    $ul = $('<ul></ul>')

    showing_placeholders = false
    index = 0

    for document in document_list.documents
      $li = create_li(document || index)
      $ul.append($li)
      index++

    if !document_list.n? || document_list.n > index
      $li = create_li(index)
      $ul.append($li)
      index++

    $div = $(div)
    $div.empty()
    $div.append($ul) if index > 0

    if showing_placeholders
      start = $ul.find('.placeholder:eq(0)').prevAll().length
      end = start + DOCUMENT_LIST_REQUEST_SIZE
      $div.trigger('document_list_view:need_more_documents', start, end)

  set_document_list = (new_document_list) ->
    document_list = new_document_list
    redraw()

  add_slice = (start, end, documents) ->
    redraw()

  on_ = (event, callback) ->
    $(div).on("document_list_view:#{event}", (e, node) -> callback(node))

  # Send clicks to controller
  $(div).on 'click', (e) ->
    e.preventDefault()
    $a = $(e.target).closest('a')
    return if !$a.length

    href = $a.attr('href')
    parts = href.split(/-/g)
    if parts[0] == 'document'
      $(div).trigger('document_list_view:document_clicked', [ +parts[parts.length-1] ])
    else
      $(div).trigger('document_list_view:unloaded_document_clicked', [ +parts[parts.length-1] ])

  redraw()

  {
    redraw: redraw,
    set_document_list: set_document_list,
    add_slice: add_slice,
    on: on_,
  }

exports = require.make_export_object('views/document_list_view')
exports.document_list_view = document_list_view
