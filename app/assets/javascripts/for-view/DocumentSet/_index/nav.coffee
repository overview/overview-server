define [ 'jquery' ], ($) ->
  $ ->
    $buttonsDiv = $('div.nav-buttons')

    setHref = (href) ->
      $(window).scrollTop(0)
      if !href || href == '#' || href == '#jobs-and-document-sets'
        $buttonsDiv.closest('.container').removeAttr('data-import-pane')
      else
        $buttonsDiv.closest('.container').attr('data-import-pane', href.substring('#import-'.length))
        window.setTimeout((-> $(href).trigger('activate')), 0) # finish jQuery's document.ready() stuff first

    $buttonsDiv.on 'click', 'a', (e) ->
      e.preventDefault() # We want to scroll ourselves
      href = $(this).attr('href')
      window.location.hash = href # which will then call setHref()

    $(window).on 'hashchange', ->
      setHref(window.location.hash)
    setHref(window.location.hash)
