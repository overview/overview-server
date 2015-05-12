require [
  'jquery'
  'bootstrap-dropdown'
], ($) ->
  POLL_DELAY = 1000 # ms between progress checks

  $ ->
    $parent = $('#main')
    $job = $parent.children('[data-document-set-id]')
    documentSetId = $job.attr('data-document-set-id')

    refresh = ->
      $.ajax
        url: '/imports.json'
        error: (xhr, textStatus, errorThrown) ->
          console.warn("Error loading /imports.json: #{textStatus}: #{errorThrown}")
        success: (data) ->
          json = data.filter((x) -> String(x.id) == documentSetId)[0]
          if json?
            # Progress has changed
            $parent.html(json.html)
          else
            # No more jobs: the document set is ready
            window.location.reload(true)
        complete: scheduleRefresh

    scheduleRefresh = -> window.setTimeout(refresh, POLL_DELAY)

    scheduleRefresh()
