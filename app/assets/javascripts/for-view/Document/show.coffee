$ = jQuery

SIDEBAR_KEY = 'views.Document.show.sidebar'
#
# Scrolling usually leaves a bit of what *was* visible still visible, to
# help viewers read those last lines. These two variables help decide how
# many pixels should remain visible after a scroll
PAGE_KEY_FRACTION = 0.8 # max percentage of an element that should scroll away
PAGE_KEY_MARGIN = 100 # min px that should remain visible

documentcloud_id_to_url = (id) ->
  sidebar = get_show_sidebar()
  "//www.documentcloud.org/documents/#{id}?sidebar=#{sidebar && 'true' || 'false'}"

get_show_sidebar = () ->
  localStorage.getItem(SIDEBAR_KEY) == "true"

set_show_sidebar = (show_sidebar) ->
  localStorage.setItem(SIDEBAR_KEY, "#{show_sidebar}")

scroll_element_by_pages = (el, n) ->
  element_height = el.clientHeight
  page_height = Math.round(Math.max(
    element_height * PAGE_KEY_FRACTION, element_height - PAGE_KEY_MARGIN
  ))
  el.scrollTop += n * page_height

# Loads Twitter's "widgets.js", only if it hasn't been loaded already
#
# Use the load_twitter_script().done(YOUR CODE HERE) to do stuff only when
# widgets.js is loaded.
load_twitter_script = (() ->
  script_task = undefined

  () ->
    if !script_task
      script_task = $.getScript('//platform.twitter.com/widgets.js')
      script_task.done(-> twitter_script_loaded = true)
    script_task
)()

replace_twitter_blockquote = ($blockquote) ->
  console.log("Requesting from blockquote", $blockquote)
  load_twitter_script()
    .done ->
      console.log("Loaded widgets.js")
      twttr.widgets.load($blockquote[0])

# Applies Twitter's JS to all tweets on the page.
#
# A tweet is a blockquote[data-twitter-url]. Its contents will be replaced.
refresh_tweets = () ->
  $('blockquote.twitter-tweet').each ->
    replace_twitter_blockquote($(this))

$ ->
  $title = $('h3')

  $csv = $('.type-CsvImportDocument')

  $dc = $('.type-DocumentCloudDocument')
  $dc_iframe = $dc.find('iframe')
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
      $dc_iframe.attr('src', 'about:blank')

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
    $dc_iframe.one 'load', ->
      $dc_iframe.attr('data-documentcloud-id', document.documentcloud_id)
      refresh_dc_iframe()

    $dc.show()

  load_any_document = (document) ->
    $csv.empty()
    $csv.hide()
    $dc.hide()
    # Replace our URL with the new ID after the final "/"
    window.location.replace("#{window.location}".replace(/\d*$/, document.id))

  $dc_sidebar_toggle.on 'click', (e) ->
    e.preventDefault()
    sidebar = get_show_sidebar()
    set_show_sidebar(!sidebar)
    refresh_dc_sidebar_toggle()
    refresh_dc_iframe()

  window.load_document = (document) ->
    set_title(document.title)

    if document.documentcloud_id
      load_documentcloud_document(document)
    else
      load_any_document(document)

  refresh_dc_sidebar_toggle()
  refresh_dc_iframe()
  refresh_tweets()

  scroll_by_pages = {
    csv: (n) ->
      $pre = $csv.find('pre')
      if $pre.length
        scroll_element_by_pages($pre[0], n)
      else
        # can't. https://github.com/overview/overview-server/issues/270

    dc: (n) ->
      # can't. See https://github.com/overview/overview-server/issues/270
  }

  window.scroll_by_pages = (n) ->
    if $csv.is(':visible')
      scroll_by_pages.csv(n)
    else if $dc_iframe.is(':visible')
      scroll_by_pages.dc(n)
