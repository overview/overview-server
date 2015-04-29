require [
  'jquery'
], ($) ->
  $iframe = $('<iframe name="export-document-set" src="about:blank"></iframe>')

  timeout = null

  reset = ->
    if timeout?
      clearTimeout(timeout)
      timeout = null

  scheduleRefreshHeight = ->
    timeout ||= window.setTimeout(refreshHeight, 100)

  refreshHeight = ->
    timeout = null
    return scheduleRefreshHeight() if $iframe.attr('src') == 'about:blank'
    return scheduleRefreshHeight() if !$iframe.parent()[0].clientWidth
    height = $iframe[0].contentDocument?.body?.offsetHeight || 0
    return scheduleRefreshHeight() if !height
    $iframe.css(height: height)

  $ ->
    $modal = $('#export-modal')
    $modal.find('.modal-body').append($iframe)

    $(document).on 'click', 'a.show-export-options', (e) ->
      e.preventDefault()
      documentSetId = $(e.currentTarget).attr('data-document-set-id')
      $iframe.attr('src', 'about:blank')
      $iframe.attr('src', "/documentsets/#{documentSetId}/export")
      $modal.modal('show')
      scheduleRefreshHeight()
