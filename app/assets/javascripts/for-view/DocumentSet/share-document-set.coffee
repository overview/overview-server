require [
  'jquery'
], ($) ->
  $iframe = $('<iframe name="share-document-set" src="about:blank"></iframe>')

  refreshHeight = (requireNonZero) ->
    requireNonZero = false if $iframe.attr('src') == 'about:blank'
    height = $iframe[0].contentDocument?.body?.offsetHeight || 0
    $iframe.css(height: height)

    # Firefox sometimes has height:0 even after the load event
    if requireNonZero && height == 0
      setTimeout(refreshHeight, 100)

  $ ->
    $modal = $('#sharing-options-modal')
    $modal.find('.modal-body').append($iframe)

    # Listen for the iframe to say it has resized itself.
    # We don't check security, since it doesn't matter how many times this
    # method is called.
    window.addEventListener('message', refreshHeight, false)
    $iframe.on('load', refreshHeight)

    $(document).on 'click', 'a.show-sharing-settings', (e) ->
      e.preventDefault()
      documentSetId = $(e.currentTarget).attr('data-document-set-id')
      $iframe.attr('src', 'about:blank')
      refreshHeight()
      $iframe.attr('src', "/documentsets/#{documentSetId}/users")
      $modal.modal('show')
