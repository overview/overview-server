define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListTitle')

  getTitleHtml = (nDocuments, titleString) ->
    if nDocuments?
      nDocumentsHtml = "<strong>#{_.escape(t('num_documents', nDocuments))}</strong>"
      if titleString
        _.escape(titleString).replace('%s', nDocumentsHtml)
      else
        nDocuments
    else
      _.escape(t('loading'))

  documentListToSettings = (documentList) ->
    params = documentList.params
    nDocuments = documentList.get('length')

    titleHtml: getTitleHtml(nDocuments, params.title)
    className: if nDocuments? then 'loaded' else 'loading'
    nDocuments: nDocuments

  # Shows what's currently selected
  #
  # Usage:
  #
  #   documentList = new DocumentList(...)
  #   view = new SelectionTitle({
  #     documentList: documentList
  #   })
  #   documentList2 = new DocumentList(...)
  #   view.setDocumentList(documentList2)
  class DocumentListTitleView extends Backbone.View
    id: 'document-list-title'

    template: _.template('''
      <span class="show-list-link">
        <a href="#" class="show-list"><i class="icon icon-chevron-left"></i> <%- t('list') %></a>
      </span>
      <h4></h4>
    ''')

    events:
      'click .show-list': '_onClickShowList'

    initialize: ->
      throw 'Must supply options.documentList, a DocumentList' if 'documentList' not of @options
      throw 'Must supply options.state, a State' if 'state' not of @options

      @initialRender()

      @state = @options.state
      @listenTo(@state, 'change:document', @render)
      @setDocumentList(@options.documentList)

    initialRender: ->
      @$el.html(@template(t: t))
      @ui =
        title: @$('h4')
        showList: @$('.show-list')

    render: ->
      settings = if @documentList?
        documentListToSettings(@documentList)
      else
        titleHtml: ''
        className: ''
        nDocuments: null

      @ui.title.html(settings.titleHtml)
      @$el.attr(class: settings.className)
      @

    _onClickShowList: (e) ->
      e.preventDefault()
      @state.set(document: null)

    setDocumentList: (documentList) ->
      for object in (@transientObjectsToListenTo || [])
        @stopListening(object)
      @transientObjectsToListenTo = []
      @documentList = documentList
      if @documentList?
        # Listen for number of docs changing and to know when we're loading)
        @listenTo(@documentList, 'change', @render)
        @transientObjectsToListenTo.push(@documentList)

        # Listen for tag name change.
        #
        # HUGE HACK ahead!
        #
        # We want to re-render the title when a tag name changes.
        # But we do _not_ want to change the document list! So our huge hack
        # is to set the title on the existing, supposedly-immutable params.
        #
        # To correct it, we need a way of setting a title without changing
        # the rest of a documentListParams. We can either make
        # documentListParams mutable or alter other code such that a title
        # change doesn't trigger a refresh.
        if @documentList.params?.documentSet?.tags?.get?
          for tagId in (@documentList.params.params?.tags || [])
            if (tag = @documentList.params.documentSet.tags.get(tagId))?
              @transientObjectsToListenTo.push(tag)
              @listenTo tag, 'change', (tag) =>
                title = @documentList.params.reset.byTag(tag).title
                @documentList.params.title = title
                @render()

      @render()
