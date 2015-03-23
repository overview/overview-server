require [
  'jquery'
], ($) ->
  $ ->
    $modal = $('#sharing-options-modal')
    $modalBody = $modal.find('.modal-body')

    stopListening = ->

    $('div.document-sets').on 'click', 'a.show-sharing-settings', (e) ->
      e.preventDefault()
      documentSetId = $(e.currentTarget).closest('[data-document-set-id]').attr('data-document-set-id')

      stopListening()

      $iframe = $('<iframe></iframe>')
        .attr('src', "/documentsets/#{documentSetId}/users")
        .appendTo($modalBody.empty())

      refreshHeight = (e) ->
        height = $iframe[0].contentDocument?.body?.offsetHeight ? 0
        $iframe.css(height: height)

      # Listen for the iframe to say it has resized itself.
      # We don't check security, since it doesn't matter how many times this
      # method is called.
      window.addEventListener('message', refreshHeight, false)
      $iframe.on('load', refreshHeight)
      stopListening = ->
        window.removeEventListener('load', refreshHeight, false)
        $iframe.off()

      $modal.modal('show')

      refreshHeight()
