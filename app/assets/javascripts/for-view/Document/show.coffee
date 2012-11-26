$ = jQuery

SIDEBAR_KEY = 'views.Document.show.sidebar'

documentcloud_id_to_url = (id) ->
  sidebar = get_show_sidebar()
  "https://www.documentcloud.org/documents/#{id}?sidebar=#{sidebar && 'true' || 'false'}"

get_show_sidebar = () ->
  localStorage.getItem(SIDEBAR_KEY) == "true"

set_show_sidebar = (show_sidebar) ->
  localStorage.setItem(SIDEBAR_KEY, "#{show_sidebar}")

$ ->
  $title = $('h3')

  $csv = $('.type-CsvImportDocument')

  $dc = $('.type-DocumentCloudDocument')
  $dc_iframe = $('iframe')
  $dc_sidebar_toggle = $('.toggle-sidebar')

  refresh_dc_sidebar_toggle = () ->
    sidebar = get_show_sidebar()
    text_key = "data-m-#{sidebar && 'disable' || 'enable'}"
    text = $dc_sidebar_toggle.attr(text_key)
    $dc_sidebar_toggle.text(text)

  refresh_dc_iframe = () ->
    id = $dc_iframe.attr('data-documentcloud-id')
    if id
      url = documentcloud_id_to_url(id)
      $dc_iframe.attr('src', url)
    else
      $dc_iframe.attr('src', '')

  set_title = (title) ->
    $title.text(title)

  load_documentcloud_document = (document) ->
    $csv.empty()
    $csv.hide()

    # Clear the iframe.
    #
    # We can't use DOM manipulation, because on dev machines we use
    # http:// but DocumentCloud is at https:// so we don't have access
    # to the iframe's contents.
    $dc_iframe.attr('src', 'about:blank')

    # Set the new iframe src
    # Do it in a callback, so the iframe has a chance to be cleared.
    $dc_frame.once 'load', ->
      $dc_iframe.attr('data-documentcloud-id', document.documentcloud_id)
      refresh_dc_iframe()

    $dc.show()

  load_any_document = (document) ->
    $csv.empty()
    $csv.hide()
    $dc.hide()
    # Replace our URL with the new ID after the final "/"
    window.location = "#{window.location}".replace(/\d*$/, document.id)

  $dc_sidebar_toggle.on 'click', (e) ->
    e.preventDefault()
    sidebar = get_show_sidebar()
    set_show_sidebar(!sidebar)
    refresh_dc_sidebar_toggle()
    refresh_dc_iframe()

  window.load_document = (document) ->
    set_title(document.title || '')

    if document.documentcloud_id
      load_documentcloud_document(document)
    else
      load_any_document(document)

  refresh_dc_sidebar_toggle()
  refresh_dc_iframe()
