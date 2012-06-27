class DocumentContentsView
  constructor: (@div, @selection, @router) ->
    @_last_url = undefined

    @selection.observe(=> this._refresh())
    this._refresh()

  _get_focus_url: () ->
    document = @selection.documents[0]
    documentid = document?.id? && document.id || document
    documentid && @router.route_to_path('document_view', documentid)

  _find_or_create_iframe: () ->
    $div = $(@div)

    $iframe = $div.find('iframe')
    if !$iframe.length
      $iframe = $('<iframe frameborder="0" width="1" height="1"></iframe>')
      $div.append($iframe)

    $iframe[0]

  _refresh: () ->
    url = this._get_focus_url()
    return if url == @_last_url

    if !url?
      $(@div).empty()
    else
      iframe = this._find_or_create_iframe()
      iframe.src = url

    @_last_url = url

exports = require.make_export_object('views/document_contents_view')
exports.DocumentContentsView = DocumentContentsView
