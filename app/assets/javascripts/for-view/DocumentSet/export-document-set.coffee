require [
  'jquery'
], ($) ->
  $iframe = $('<iframe src="about:blank"></iframe>')

  refreshHeight = (e) ->
    height = $iframe[0].contentDocument?.body?.offsetHeight || 0
    $iframe.css(height: height)

  $ ->
    $modal = $('#export-modal')
    $modal.find('.modal-body').append($iframe)

    console.log($modal)

    $iframe.on('load', refreshHeight)

    $(document).on 'click', 'a.show-export-options', (e) ->
      e.preventDefault()
      documentSetId = $(e.currentTarget).attr('data-document-set-id')
      $iframe.attr('src', 'about:blank')
      refreshHeight()
      $iframe.attr('src', "/documentsets/#{documentSetId}/export")
      $modal.modal('show')
