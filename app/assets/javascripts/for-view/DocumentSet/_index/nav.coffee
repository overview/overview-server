define [ 'jquery' ], ($) ->
  $ ->
    $buttonsDiv = $('div.nav-buttons')

    setHref = (href) ->
      $(window).scrollTop(0)

      if !href || href == '#' || href == '#jobs-and-document-sets'
        if $('#jobs-and-document-sets .document-sets li').length == 0 && $('.document-set-creation-jobs li').length == 0
          # no documents
          window.location.hash = 'import-public'

        window.setTimeout((-> $('#jobs-and-document-sets').css(opacity: 1).css(filter: '~"alpha(opacity=@{opacity})"')), .35) #skip the animation the first time

        $buttonsDiv.closest('.container').removeAttr('data-import-pane')
      else
        $buttonsDiv.closest('.container').attr('data-import-pane', href.substring('#import-'.length))
        window.setTimeout((-> $(href).trigger('activate')), 0) # finish jQuery's document.ready() stuff first
        $('#jobs-and-document-sets').css(opacity: 1).css(filter: '~"alpha(opacity=@{opacity})"')

    $buttonsDiv.on 'click', 'a', (e) ->
      href = $(this).attr('href')
      if href == window.location.href
        e.preventDefault() # don't scroll down
      else
        # pass the click through

    $(window).on 'hashchange', ->
      setHref(window.location.hash)
    setHref(window.location.hash)

