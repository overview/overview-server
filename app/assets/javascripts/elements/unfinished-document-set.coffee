m = {
  queue_description: (n) -> "Number of jobs to process before this one: #{n}",
}

WAIT_INTERVAL = 500 # ms after one request completes before another starts

$ ->
  $('body.document-set-index li.unfinished').each ->
    $li = $(this)
    $a = $li.find('h2 a')
    json_href = "#{$a.attr('href')}.json"

    done = (data) ->
      $li.replaceWith(data.html)

    state_description = (data) ->
      if data.n_jobs_ahead_in_queue
        m.queue_description(data.n_jobs_ahead_in_queue)
      else
        data.state_description

    progress = (data) ->
      $li.find('progress').attr('value', data.percent_complete)
      $li.find('.state').text(data.state)
      $li.find('.state-description').text(state_description(data))

    refresh = ->
      $.get json_href, (data) ->
        if data.html?
          done(data)
        else
          progress(data)
          window.setTimeout(refresh, WAIT_INTERVAL)

    $a.click((e) -> e.preventDefault())
    window.setTimeout(refresh, WAIT_INTERVAL)
