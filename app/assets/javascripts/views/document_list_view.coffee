observable = require('models/observable').observable
ColorTable = require('views/color_table').ColorTable

DEFAULT_OPTIONS = {
  buffer_documents: undefined, # retrieve all documents
  ul_style: undefined, # CSS applied to main <ul> -- used for testing
  li_style: undefined, # CSS applied to every <li> -- used for testing
}

$ = jQuery

class DocumentListView
  observable(this)

  constructor: (@div, @cache, @document_list, @state, options={}) ->
    @color_table = new ColorTable()
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
    this._attach_tag_changed()
    this._attach_tag_removed()

  _attach_click: () ->
    $div = $(@div)
    $div.on 'click', (e) =>
      e.preventDefault()

      $a = $(e.target).closest('a')

      docid = if $a.length
        href = $a.attr('href') # e.g., "document-132"
        +(href.split(/-/g)[1])
      else
        undefined

      this._notify('document-clicked', docid, { meta: e.ctrlKey || e.metaKey || false, shift: e.shiftKey || false })

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
    $('ul', @div).on('scroll', this._refresh_need_documents.bind(this))

  _get_bottom_need_document_index: () ->
    return undefined if !@_buffer_documents?
    # Assumes there's no padding between documents
    document_height = this._get_document_height()
    $ul = $('ul', @div)
    ul_bottom = $ul.height() + $ul.scrollTop()
    Math.ceil(ul_bottom / (document_height || 1)) + @_buffer_documents

  _attach_selection: () ->
    @state.observe('selection-changed', this._refresh_selection.bind(this))

  _attach_document_list: () ->
    @_document_list_callback = () => this._redraw()
    @document_list.observe(@_document_list_callback)

  _detach_document_list: () ->
    @document_list.unobserve(@_document_list_callback)

  _attach_document_changed: () ->
    @cache.document_store.observe('document-changed', (document) => this._update_document(document))

  _attach_tag_changed: () ->
    @cache.tag_store.observe('tag-changed', (tag) => this._update_tag(tag))

  _attach_tag_removed: () ->
    @cache.tag_store.observe('tag-removed', (tag) => this._redraw())

  _update_document_a_tagids: ($tags, tagids) ->
    return if @cache.tag_store.tags.length == 0 # XXX remove when we're sure /root has been loaded before we get here
    $tags.empty()
    for tagid in tagids
      tag = @cache.tag_store.find_tag_by_id(tagid)
      color = tag.color || @color_table.get(tag.name)
      $span = $("<span style=\"background-color: #{color}\"/>")
      $span.attr('title', tag.name)
      $tags.append($span)

  _update_document: (document) ->
    $tags = $("a[href=#document-#{document.id}] span.tags")
    this._update_document_a_tagids($tags, document.tagids || [])

  _update_tag: (tag) ->
    tagid = tag.id
    for document in @document_list.documents
      tagids = document?.tagids
      index = tagids?.indexOf(tagid)
      if index? && index != -1
        this._update_document(document)

  _document_to_dom_node: (document) ->
    $a = $('<a></a>')
      .text(document.title)
      .attr('href', "#document-#{document.id}")

    $tags = $('<span class="tags"/>')
    this._update_document_a_tagids($tags, document.tagids || [])
    $a.prepend($tags)

    $a

  _generate_title_html: (n) ->
    # "4 documents"
    num_documents_text = i18n('views.DocumentSet.show.document_list.title.num_documents', n)

    selection = @document_list.selection
    num_nodes = selection.nodes.length
    num_tags = selection.tags.length

    selection_description_text = if num_nodes == 1 && num_tags == 0
      node = @cache.on_demand_tree.nodes[selection.nodes[0]]
      i18n('views.DocumentSet.show.document_list.title.node', node.description)
    else if num_tags == 1 && num_nodes == 0
      tag = @cache.tag_store.find_tag_by_id(selection.tags[0])
      i18n('views.DocumentSet.show.document_list.title.tag', tag.name)
    else # plural
      i18n('views.DocumentSet.show.document_list.title.n_nodes_and_n_tags', num_nodes, num_tags)

    i18n('views.DocumentSet.show.document_list.title_html', num_documents_text, selection_description_text)

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

    $ul = $div.children('ul')
    scroll_top = if $ul.length then $ul[0].scrollTop else 0

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
      documents = @document_list.get_placeholder_documents(@cache)

    $h4 = $div.children('h4')
    if $h4.length == 0
      $h4 = $('<h4></h4>')
      $div.prepend($h4)

    $ul = $div.children('ul') # again, in case it was removed
    if $ul.length == 0
      $ul = $('<ul></ul>')
      $ul.attr('style', @_ul_style) if @_ul_style?
      $div.append($ul)
      this._attach_scroll()

    # Replace num_documents
    if !n?
      $h4.text(i18n('views.DocumentSet.show.document_list.loading'))
    else
      $h4.html(this._generate_title_html(n))

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
        else
          this._update_document(document)
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

    $ul[0].scrollTop = scroll_top

    this._refresh_selection()
    this._refresh_need_documents()

  _refresh_selection: () ->
    $div = $(@div)
    $div.find('.selected').removeClass('selected')
    for document_or_id in @state.selection.documents
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

exports = require.make_export_object('views/document_list_view')
exports.DocumentListView = DocumentListView
