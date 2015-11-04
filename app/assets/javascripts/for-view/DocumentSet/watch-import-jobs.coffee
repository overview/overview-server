POLL_DELAY = 500 # ms between requests. Shorter makes the progress bar smoother.
RETRY_DELAY = 5000 # ms before a failed request is retried.

define [ 'jquery', 'elements/jquery-time_display' ], ($) ->
  # 1. Find all the running jobs on the page (look for <progress>).
  # 2. Schedule recurrent updates. For each response, update/remove all <li>
  #    elements.
  # 3. Stop when there are no more <li> elements.

  # Returns a hash of key -> <li>.
  #
  # The key is "#{documentSetId}-#{index}".
  loadState = ->
    ret = {}
    $('ul.import-jobs li').each ->
      documentSetId = @parentNode.parentNode.getAttribute("data-document-set-id")
      index = $(@).prevAll().length
      key = "#{documentSetId}-#{index}"
      ret[key] = @
    ret

  # Returns a hash of key -> { progress, description }
  #
  # The key is "#{documentSetId}-#{index}". Progress is a Number between 0 and
  # 1 (or `null`). Description is an already-i18n-ized String (or `null`).
  parseResponse = (json) ->
    ret = {}
    lastIndex = {} # hash of documentSetId -> <null|1|2|...>
    for node in json
      lastIndex[node.documentSetId] ?= -1
      lastIndex[node.documentSetId] += 1
      index = lastIndex[node.documentSetId]
      key = "#{node.documentSetId}-#{index}"
      ret[key] = node
    ret

  # Returns an updated `state` (leaving the original unmodified).
  #
  # 1. Removes all <li>s that have no match in `response`, and all <ul>s that
  #    have thus become empty.
  # 2. Updates all <li>s that have a match in `response`
  #
  # Ignores any entry in `response` that is not in `state`.
  updatePage = (state, response) ->
    # Remove completed nodes
    newState = {}
    for key, el of state
      if key not of response
        # Remove the element
        $li = $(el)
        $ul = $li.parent()
        $li.remove()
        if $ul.children().length == 0
          redirectUrl = $ul.attr('data-redirect-when-finished')
          if redirectUrl
            window.location = redirectUrl
          else
            $ul.remove()
      else
        # Update the element
        info = response[key]
        $li = $(el)
        $li.find('.progress-description').text(info.description || '')
        $li.find('progress').attr('value', info.progress ? null)

        newState[key] = el

    newState = state

  $ ->
    state = loadState()

    maybeQueueRefresh = ->
      if Object.keys(state).length > 0
        $.ajax
          url: '/imports.json'
          success: (json) ->
            response = parseResponse(json)
            state = updatePage(state, response)
            window.setTimeout(maybeQueueRefresh, POLL_DELAY)
          error: (xhr, textStatus, errorThrown) ->
            console.warn(xhr, textStatus, errorThrown)
            window.setTimeout(maybeQueueRefresh, RETRY_DELAY)

    maybeQueueRefresh()
