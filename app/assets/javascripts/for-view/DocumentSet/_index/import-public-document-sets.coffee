define [ 'jquery' ], ($) ->
  # Create a method, "refresh", which refreshes the contents of the
  # #import-public-document-sets element from the server.
  $ ->
    load_url = ($div, url) ->
      $div.load url, ->
        # Make inserted pagination links load in the div, not window.location
        $div.find('.pagination').on 'click', 'a', (e) ->
          $a = $(e.currentTarget)
          href = $a.attr('href')
          load_url($div, href)

    show = ->
      $div = $('#import-public-document-sets')
      url = $div.attr('data-source-url')
      load_url($div, url)

    $('#import-public').on('activate', show)
    $('iframe.quick-start-video').attr('src', 'https://player.vimeo.com/video/77729177')
