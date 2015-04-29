require [
  'jquery'
], ($) ->
  $iframe = $('<iframe name="export-document-set" src="about:blank"></iframe>')

  refreshHeight = (requireNonZero) ->
    requireNonZero = false if $iframe.attr('src') == 'about:blank'
    height = $iframe[0].contentDocument?.body?.offsetHeight || 0
    $iframe.css(height: height)

    # Firefox sometimes has height:0 even after the load event
    if requireNonZero && height == 0
      setTimeout(refreshHeight, 100)

  $ ->
    $modal = $('#export-modal')
    $modal.find('.modal-body').append($iframe)

    $iframe.on('load', refreshHeight)

    $(document).on 'click', 'a.show-export-options', (e) ->
      e.preventDefault()
      documentSetId = $(e.currentTarget).attr('data-document-set-id')
      $iframe.attr('src', 'about:blank')
      refreshHeight()
      $iframe.attr('src', "/documentsets/#{documentSetId}/export")
      $modal.modal('show')
