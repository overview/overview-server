require [
  'jquery'
], ($) ->
  $iframe = $('<iframe src="about:blank"></iframe>')

  refreshHeight = (e) ->
    height = $iframe[0].contentDocument?.body?.offsetHeight || 0
    $iframe.css(height: height)

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
