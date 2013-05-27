define [ 'backbone', 'i18n' ], (Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.DocumentListCursor.#{key}", args...)

  Backbone.View.extend
    id: 'document-list-cursor'

    events:
      'click a.next': '_onClickNext'
      'click a.previous': '_onClickPrevious'
      'click a.list': '_onClickList'

    template: _.template("""
      <h4><%- t('title', cursorIndex + 1, nDocuments) %></h4>
      <ul class="actions">
        <li><a href="#" class="list"><i class="icon-th-list"></i><%- t('list') %></a></li>
        <li><a href="#" class="next <%= cursorIndex + 1 < nDocuments ? '' : 'disabled' %>"><i class="icon-arrow-down"></i><%- t('next') %></a></li>
        <li><a href="#" class="previous <%= cursorIndex ? '' : 'disabled' %>"><i class="icon-arrow-up"></i><%- t('previous') %></a></li>
      </ul>
    """)

    initialize: ->
      throw 'Must pass options.selection, a Backbone.Model with a "cursorIndex" property' if !@options.selection
      throw 'Must pass options.documentList, undefined or a Backbone.Model with a "n" property' if 'documentList' not of @options

      @selection = @options.selection
      @documentList = @options.documentList

      @listenTo(@selection, 'change:cursorIndex', => @render())
      @setDocumentList(@options.documentList)

    render: ->
      cursorIndex = @selection.get('cursorIndex')
      nDocuments = @documentList?.get('n') || 0

      html = if !nDocuments || !cursorIndex? || cursorIndex >= nDocuments
        ''
      else
        @template({ nDocuments: nDocuments, cursorIndex: cursorIndex, t: t })

      @$el.html(html)

      this

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
      @stopListening(@documentList) if @documentList?
      @documentList = documentList
      @listenTo(@documentList, 'change:n', => @render()) if @documentList?
      @render()
