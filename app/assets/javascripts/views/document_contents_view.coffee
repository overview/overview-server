class DocumentContentsView
  constructor: (@div, @cache, @state) ->
    @_last_docid = undefined
    @iframe = undefined

    @state.observe('selection-changed', this._refresh.bind(this))

    this._refresh()

  _get_docid: () ->
    if @state.selection.documents.length
      @state.selection.documents[0]
    else
      @state.selection.documents_from_cache(@cache)[0]?.id

  _get_iframe_url: (document) ->
    @cache.server.router.route_to_path('document_view', document.id)

  _create_iframe: (document) ->
    $iframe = $('<iframe frameborder="0" width="1" height="1"></iframe>')
    url = this._get_iframe_url(document)
    $iframe.attr('src', this._get_iframe_url(document))
    $div = $(@div)
    $div.empty()
    $div.append($iframe)
    @iframe = $iframe[0]

  _refresh: () ->
    docid = this._get_docid()
    return if docid == @_last_docid
    @_last_docid = docid

    done = false

    if docid?
      document = @cache.document_store.documents[docid]

      if @iframe?
        try
          @iframe.contentWindow.load_document(document)
          done = true
        catch e
          # ignore
      else
        this._create_iframe(document)
        done = true
    else
      if @iframe?
        try
          @iframe.contentWindow.load_documentcloud(undefined)
          done = true
        catch e
          #ignore

    if !done
      this._create_iframe(document)

exports = require.make_export_object('views/document_contents_view')
exports.DocumentContentsView = DocumentContentsView
