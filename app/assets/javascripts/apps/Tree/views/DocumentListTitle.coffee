define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.Tree.show.DocumentListTitle')

  getTemplateParams = (documentList) ->
    params = documentList.params
    i18nParams = params.toI18n()
    type = i18nParams[0]
    i18nParams = params.toI18n()
    nDocuments = documentList.get('length')
    nDocumentsText = t('num_documents', nDocuments) if nDocuments?

    ret =
      editLinkHtml: ''
      titleHtml: ''
      className: ''

    if type == 'searchResult'
      searchResult = params.params[0]
      state = searchResult.get('state')
      state = 'InProgress' if state == 'Complete' && !nDocuments?
      ret.className = "search-#{state.toLowerCase()}"
      if state == 'Complete'
        ret.titleHtml = t('searchResult.Complete.title_html', nDocumentsText, i18nParams.slice(1)...)
        ret.className += ' loaded'
      else
        ret.titleHtml = t("searchResult.#{state}.title_html", i18nParams.slice(1)...)
        if state == 'Error'
          ret.className += ' loaded'
        else
          ret.className += ' loading'
    else if !nDocuments?
      ret.titleHtml = t('loading')
      ret.className = 'loading'
    else
      ret.titleHtml = t("#{type}.title_html", nDocumentsText, i18nParams.slice(1)...)
      ret.className = 'loaded'

    if type of { tag: null, node: null}
      ret.editLinkHtml = "<a href='#' class='edit'>#{t("#{type}.edit")}</a>"

    ret

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
  #
  # Events:
  #
  # * edit-tag: (tag) indicates the user requests a tag edit
  # * edit-node: (node) indicates the user requests a node edit
  class DocumentListTitleView extends Backbone.View
    id: 'document-list-title'

    template: _.template('''
      <div>
        <%= editLinkHtml %>
        <h4><%= titleHtml %></h4>
      </div>
    ''')

    events:
      'click a.edit': '_onEditClicked'

    initialize: ->
      throw 'Must supply options.documentList, a DocumentList' if 'documentList' not of @options

      @setDocumentList(@options.documentList)

    render: ->
      if @documentList?
        templateParams = getTemplateParams(@documentList)

        html = @template(templateParams)

        @$el.html(html).attr(class: templateParams.className)
      else
        @$el.html('').attr(class: '')

    _onEditClicked: (e) ->
      e.preventDefault()
      params = @documentList.params
      type = params.type
      param = params.params[0]
      @trigger("edit-#{type}", param)

    setDocumentList: (documentList) ->
      @stopListening()
      @documentList = documentList
      if @documentList?
        # Listen for number of docs changing and to know when we're loading)
        @listenTo(@documentList, 'change', @render)

        # Listen for tag/node name change
        param = @documentList.params?.params?[0]
        if param? && 'on' of param # it's Backbone.Model-ish
          @listenTo(param, 'change', @render)

      @render()
