define [
  'backbone'
  'i18n'
], (Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.DocumentListCursor.#{key}", args...)

  # Shows the Document corresponding to the user's cursor.
  #
  # There are three states (HTML class names) this view can be in:
  #
  # * showing-document: the cursor points at a document
  # * showing-unloaded-document: the cursor points at a document that is in the
  #   DocumentList's range but is not in the DocumentList's collection -- that
  #   is, a document that presumably we're fetching from the server.
  # * not-showing-document: the cursor does not point at a document
  #
  # The following options must be passed:
  #
  # * documentList: a Backbone.Model with an "n" attribute and
  #   describeSelection() method. (See
  #   Cache.describeSelectionWithoutDocuments().) documentList.documents must be
  #   a Backbone.Collection of Backbone.Model instances, with attributes
  #   such as would be passed to DocumentDisplay/App.setDocument()
  # * selection: A Backbone.Model with "cursorIndex" (maybe undefined integer)
  #   and "selectedIndices" (maybe-empty Array of integers). 0 is the first
  #   index.
  # * documentDisplayApp: a DocumentDisplay/App constructor, such that
  #   "new options.documentDisplayApp({ el: HTMLElement }) will create an
  #   object with a setDocument() method.
  Backbone.View.extend
    events:
      'click a.next': '_onClickNext'
      'click a.previous': '_onClickPrevious'
      'click a.list': '_onClickList'

    templates:
      root: _.template("""
        <a href="#" class="list"><i class="icon-th-list"></i><%- t('list') %></a>
        <div class="box">
          <header></header>
          <article></article>
        </div>
        """)
      header: _.template("""
        <a href="#" class="previous <%= cursorIndex ? '' : 'disabled' %>"><i class="icon-arrow-left"></i><span><%- t('previous') %></span></a>
        <a href="#" class="next <%= cursorIndex + 1 < nDocuments ? '' : 'disabled' %>"><i class="icon-arrow-right"></i><span><%- t('next') %></span></a>
        <div class="position"><%= t('position_html', cursorIndex + 1, nDocuments) %></div>
        <div class="selection"><%= selectionHtml %></div>
        <h2><%- (document && document.get('title')) ? t('title', document.get('title')) : t('title.empty') %></h2>
        <ul class="tags">
          <% _.each(tags, function(tag) { %>
            <li class="tag" data-cid="<%- tag.cid %>">
              <div class="tag" style="background-color: <%= tag.get('color') %>;">
                <span class="name"><%- tag.get('name') %></span>
              </div>
            </li>
          <% }); %>
        </ul>
        <h3><%- (document && document.get('description')) ? t('description', document.get('description')) : t('description.empty') %></h3>
      """)

    initialize: ->
      throw 'Must pass options.selection, a Backbone.Model with a "cursorIndex" property' if !@options.selection
      throw 'Must pass options.documentList, undefined or a Backbone.Model with a "n" property and "selection" property' if 'documentList' not of @options
      throw 'Must pass options.documentDisplayApp, a DocumentDisplay App constructor' if 'documentDisplayApp' not of @options
      throw 'Must pass options.tags, a Collection of Backbone.Tags' if !@options.tags
      throw 'Must pass options.tagIdToModel, a function mapping id to Backbone.Model' if !@options.tagIdToModel

      @tagIdToModel = @options.tagIdToModel
      @selection = @options.selection
      @documentList = @options.documentList

      @initialRender()

      @documentDisplayApp = new @options.documentDisplayApp({ el: @documentEl })

      @listenTo(@options.tags, 'change', => @renderHeader()) # even an ID change
      @listenTo(@selection, 'change:cursorIndex', => @render())
      @setDocumentList(@options.documentList)

    initialRender: ->
      html = @templates.root({ t: t })
      @$el.html(html)
      @$headerEl = @$('header')
      @$documentEl = @$('article')
      @headerEl = @$headerEl[0]
      @documentEl = @$documentEl[0]

      this

    _renderHeader: (maybeDocument) ->
      cursorIndex = @selection.get('cursorIndex')
      nDocuments = @documentList?.get('n') || 0

      tags = (@tagIdToModel(tagid) for tagid in maybeDocument?.get('tagids') || [])
      tags.sort((a, b) -> (a.attributes.name || '').toLowerCase().localeCompare((b.attributes.name || '').toLowerCase()))

      selectionI18n = @documentList?.describeSelection() || [ 'other' ]
      selectionI18n[0] = "selection.#{selectionI18n[0]}_html"
      if selectionI18n[1]
        selectionI18n[1] = _.escape(selectionI18n[1])
      selectionHtml = t.apply({}, selectionI18n)

      html = if !nDocuments || !cursorIndex? || cursorIndex >= nDocuments
        ''
      else
        @templates.header({
          nDocuments: nDocuments
          cursorIndex: cursorIndex
          t: t
          tags: tags
          document: maybeDocument
          selectionHtml: selectionHtml
        })

      @$headerEl.html(html)

      @$documentEl.css({ top: @$headerEl.outerHeight() })

    _getDocument: ->
      cursorIndex = @selection.get('cursorIndex')
      cursorIndex? && @documentList?.documents?.at(cursorIndex) || undefined

    _renderDocument: (maybeDocument) ->
      @documentDisplayApp.setDocument(maybeDocument?.attributes)

    renderHeader: ->
      maybeDocument = @_getDocument()
      @_renderHeader(maybeDocument)

    render: ->
      maybeDocument = @_getDocument()

      @_renderHeader(maybeDocument)

      cursorIndex = @selection.get('cursorIndex')
      @el.className = if cursorIndex?
        if maybeDocument?
          'showing-document'
        else
          'showing-unloaded-document'
      else
        'not-showing-document'

      if maybeDocument?.id != @_lastRenderedDocument?.id
        @_renderDocument(maybeDocument)
        @_lastRenderedDocument = maybeDocument

    _handleNextOrPrevious: (e, nextOrPrevious) ->
      e.preventDefault()

      if !$(e.currentTarget).hasClass('disabled')
        @trigger("#{nextOrPrevious}-clicked")

    _onClickNext: (e) -> @_handleNextOrPrevious(e, 'next')
    _onClickPrevious: (e) -> @_handleNextOrPrevious(e, 'previous')

    _onClickList: (e) ->
      e.preventDefault()
      @trigger('list-clicked')

    setDocumentList: (documentList) ->
      if @documentList?
        @stopListening(@documentList)
        if @documentList.documents?
          @stopListening(@documentList.documents)

      @documentList = documentList

      if @documentList?
        @listenTo(@documentList, 'change:n', => @render())
        if @documentList.documents
          @listenTo(@documentList.documents, 'change', => @render())
          @listenTo(@documentList.documents, 'add', => @render())
          @listenTo(@documentList.documents, 'remove', => @render())
          @listenTo(@documentList.documents, 'reset', => @render())

      @render()
