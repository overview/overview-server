require [
  'jquery'
  'bootstrap-dropdown'
], ($) ->
  POLL_DELAY = 500 # ms between progress checks -- low means smoother
  RETRY_DELAY = 5000 # ms between failed progress checks

  $ ->
    $main = $('#main')
    $ul = $main.find('ul.import-jobs')
    documentSetId = +$ul.attr('data-document-set-id')

    refresh = ->
      $.ajax
        url: '/imports.json'
        error: (xhr, textStatus, errorThrown) ->
          console.warn("Error loading /imports.json: #{textStatus}: #{errorThrown}")
          scheduleRefresh(RETRY_DELAY)
        success: (data) ->
          $ul.empty()
          for job in data when job.documentSetId == documentSetId
            $progress = $('<progress></progress>').attr('value', job.progress)
            $description = $('<span class="progress-description"></span>').text(job.description)
            $li = $('<li></li>').append($progress).append($description)
            $ul.append($li)

          if $ul.children().length == 0
            # No more jobs: the document set is ready
            window.location.reload(true)
          else
            scheduleRefresh(POLL_DELAY)

    scheduleRefresh = (timeout) -> window.setTimeout(refresh, timeout)

    refresh()
