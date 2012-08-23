WAIT_INTERVAL = 500 # ms after one request completes before another starts

$ ->
  $('body.document-set-index li.unfinished').each ->
    $li = $(this)
    $a = $li.find('h2 a')
    json_href = "#{$a.attr('href')}.json"

    done = (data) ->
      $li.replaceWith(data.html)

    progress = (data) ->
      $li.find('progress').attr('value', data.percent_complete)
      $li.find('.state').text(data.state)
      $li.find('.state-description').text(data.state_description)

    refresh = ->
      $.get json_href, (data) ->
        if data.html?
          done(data)
        else
          progress(data)
          window.setTimeout(refresh, WAIT_INTERVAL)

    $a.click((e) -> e.preventDefault())
    window.setTimeout(refresh, WAIT_INTERVAL)
