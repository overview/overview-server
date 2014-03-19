define [ 'jquery' ], ($) ->
  $ ->
    $buttonsDiv = $('div.nav-buttons')
    $container = $buttonsDiv.closest('.container')

    setHref = (href) ->
      $(window).scrollTop(0)

      if !href || href == '#' || href == '#jobs-and-document-sets'
        if $('#jobs-and-document-sets .document-sets li').length + $('.document-set-creation-jobs li').length == 0
          # no documents
          window.location.hash = 'import-public' # and loop around again
        else
          $container.removeAttr('data-import-pane')
          $('#jobs-and-document-sets').removeClass('loading')
      else
        $container.attr('data-import-pane', href.substring('#import-'.length))
        $('#jobs-and-document-sets').removeClass('loading')
        window.setTimeout((-> $(href).trigger('activate')), 0) # finish jQuery's document.ready() stuff first

      # Trigger repaint for PhantomJS. See https://github.com/ariya/phantomjs/issues/12075
      $container.addClass('blah').removeClass('blah')

    $buttonsDiv.on 'click', 'a', (e) ->
      href = $(this).attr('href')
      if href == window.location.href
        e.preventDefault() # don't scroll down
      else
        # pass the click through

    $(window).on 'hashchange', ->
      setHref(window.location.hash)
    setHref(window.location.hash)
