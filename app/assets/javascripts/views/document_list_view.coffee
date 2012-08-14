observable = require('models/observable').observable

DEFAULT_OPTIONS = {
  buffer_documents: undefined, # retrieve all documents
  ul_style: undefined, # CSS applied to main <ul> -- used for testing
  li_style: undefined, # CSS applied to every <li> -- used for testing
}

$ = jQuery

class DocumentListView
  observable(this)

  constructor: (@div, @document_store, @on_demand_tree, @tag_store, @document_list, @selection, options={}) ->
    @need_documents = [] # list of [start, end] pairs of needed documents
    @_last_a_clicked = undefined
    @_redraw_used_placeholders = false

    @_buffer_documents = options.buffer_documents || DEFAULT_OPTIONS.buffer_documents
    @_ul_style = options.ul_style || DEFAULT_OPTIONS.ul_style
    @_li_style = options.li_style || DEFAULT_OPTIONS.li_style

    this._attach()
    this._redraw()

  _attach: () ->
    this._attach_click()
    this._attach_selection()
    this._attach_document_list()
    this._attach_document_changed()
    this._attach_scroll()

  _attach_click: () ->
    $div = $(@div)
    $div.on 'click', (e) =>
      e.preventDefault()

      $a = $(e.target).closest('a')
      last_a_clicked = if !$a.length
        undefined
      else
        $a[0]

      if @_last_a_clicked != last_a_clicked
        @_last_a_clicked = last_a_clicked
        this._notify('document-clicked')

  _get_document_height: () ->
    return @_document_height if @_document_height?
    $ul = $('<ul></ul>')
    $ul.attr('style', @_ul_style) if @_ul_style?
    $li = $('<li>&nbsp;</li>')
    $li.attr('style', @_li_style) if @_li_style?
    $ul.append($li)
    $(@div).append($ul)
    @_document_height = $li.height()
    $ul.remove()
    return @_document_height

  _attach_scroll: () ->
    $(@div).scroll(=> this._refresh_need_documents())

  _get_bottom_need_document_index: () ->
    return undefined if !@_buffer_documents?
    # Assumes there's no padding between documents
    document_height = this._get_document_height()
    $div = $(@div)
    div_bottom = $div.height() + $div.scrollTop()
    Math.ceil(div_bottom / (document_height || 1)) + @_buffer_documents

  _attach_selection: () ->
    @selection.observe( => this._refresh_selection())

  _attach_document_list: () ->
    @_document_list_callback = () => this._redraw()
    @document_list.observe(@_document_list_callback)

  _detach_document_list: () ->
    @document_list.unobserve(@_document_list_callback)

  _attach_document_changed: () ->
    @document_store.observe('document-changed', (document) => this._update_document(document))

  _update_document_a_tagids: ($tags, tagids) ->
    return if @tag_store.tags.length == 0 # XXX remove when we're sure /root has been loaded before we get here
    $tags.empty()
    for tagid in tagids
      tag = @tag_store.find_tag_by_id(tagid)
      $tags.append("<span class=\"tag-color-#{tag.position}\"/>")

  _update_document: (document) ->
    $tags = $("a[href=#document-#{document.id}] span.tags")
    this._update_document_a_tagids($tags, document.tagids || [])

  _document_to_dom_node: (document) ->
    $a = $('<a></a>')
      .text(document.title)
      .attr('href', "#document-#{document.id}")

    $tags = $('<span class="tags"/>')
    this._update_document_a_tagids($tags, document.tagids || [])
    $a.prepend($tags)

    $a

  _index_to_dom_node: (index) ->
    $a = $('<a></a>')
      .text('(loading...)')
      .attr('href', "#loading-document-#{index}")

  _create_li: (document_or_index) ->
    $li = $('<li></li>')
    $li.attr('style', @_li_style) if @_li_style?

    $node = if document_or_index.id?
      this._document_to_dom_node(document_or_index)
    else
      $li.addClass('placeholder')
      this._index_to_dom_node(document_or_index)

    $li.append($node)

  _redraw: () ->
    $div = $(@div)

    documents = @document_list.documents
    n = @document_list.n

    if @_redraw_used_placeholders
      return if !n? # nothing has changed
      $div.empty() # remove all placeholders and start over

    if n is 0
      $div.empty()
      return

    if !n?
      @_redraw_used_placeholders = true
      documents = @document_list.get_placeholder_documents(@document_store, @on_demand_tree)

    $ul = $div.children('ul')

    if $ul.length == 0
      $ul = $('<ul></ul>')
      $ul.attr('style', @_ul_style) if @_ul_style?
      $div.append($ul)

    index = 0

    # Replace infix placeholders with values, when we can
    $ul.children().each (i, li) =>
      return false if i >= @document_list.documents.length
      document = @document_list.documents[i]
      if document?
        $li = $(li)
        if $li.hasClass('placeholder')
          $new_li = this._create_li(@document_list.documents[index])
          $li.replaceWith($new_li)
      index++

    # Add new elements
    for i in [index...documents.length]
      document_or_index = documents[i] || i
      $li = this._create_li(document_or_index)
      $li.addClass('placeholder') if !n?
      $ul.append($li)
      index++

    # Add a placeholder at the end
    if (n? && n > documents.length) || (!n? && !documents.length)
      $li = this._create_li(index)
      $ul.append($li)
      index++

    this._refresh_selection()
    this._refresh_need_documents()

  _refresh_selection: () ->
    $div = $(@div)
    $div.find('.selected').removeClass('selected')
    for document_or_id in @selection.documents
      documentid = document_or_id.id? && document_or_id.id || document_or_id
      $div.find("a[href=#document-#{documentid}]").addClass('selected')

  _refresh_need_documents: () ->
    documents = @document_list.documents
    n = @document_list.n

    if !n?
      this._set_need_documents([[0, undefined]])
      return

    limit = n
    if @_buffer_documents?
      limit = _.min([n, this._get_bottom_need_document_index()])

    need_documents = []

    start = undefined

    for cur in [0...limit]
      need = (cur >= documents.length || !documents[cur]?)

      if !start? && need
        start = cur

      if start? && !need
        need_documents.push([start, cur])
        start = undefined

      break if cur >= documents.length

    if start?
      need_documents.push([start, limit])

    this._set_need_documents(need_documents)

  _set_need_documents: (need_documents) ->
    changed = (need_documents.length != @need_documents.length)

    if !changed
      for i in [0...need_documents.length]
        a = need_documents[i]
        b = @need_documents[i]
        if a[0] != b[0] || a[1] != b[1]
          changed = true
          break

    if changed
      @need_documents = need_documents
      this._notify('need-documents')

  set_document_list: (document_list) ->
    this._detach_document_list()
    @document_list = document_list
    this._attach_document_list()
    $(@div).empty()
    @_redraw_used_placeholders = false
    @need_documents = []
    this._redraw()

  get_top_need_documents: () ->
    return undefined if !@need_documents
    @need_documents[0]

  last_document_id_clicked: () ->
    return undefined if !@_last_a_clicked

    href_parts = $(@_last_a_clicked).attr('href')?.split(/-/g)

    return undefined if !href_parts?

    if href_parts[0] == '#document'
      +href_parts[href_parts.length-1]
    else
      undefined #+href_parts[href_parts.length-1]

exports = require.make_export_object('views/document_list_view')
exports.DocumentListView = DocumentListView
