define [ 'jquery', 'i18n', 'elements/jquery-time_display' ], ($, i18n) ->
  t = i18n.namespaced('views.DocumentSet')

  POLL_DELAY = 500 # ms between requests. Shorter makes the progress bar smoother.
  RETRY_DELAY = 2000 # ms before a failed request is retried.
  JOB_POLL_DELAY=1000 # ms. Each reclustering document set has its own poll loop with this delay.

  # Polls /documentsets/:id/views.jzon until there are no recluster jobs.
  # Updates the text of @$el to match.
  class ReclusterWatcher
    constructor: (@$el, @documentSetId) ->
      @schedulePoll()

    schedulePoll: ->
      window.setTimeout(@poll.bind(@), JOB_POLL_DELAY)

    poll: ->
      $.getJSON("/documentsets/#{@documentSetId}/views")
        .success(@onSuccess.bind(@))
        .error(@onError.bind(@))

    jsonToCounts: (json) ->
      nViews = 0
      nJobs = 0
      for view in json
        if view.type == 'job'
          nJobs += 1
        else
          nViews += 1

      nViews: nViews
      nJobs: nJobs

    countsToText: (counts) ->
      text1 = t('_documentSet.nViews', counts.nViews)
      if counts.nJobs
        text2 = t('_documentSet.nJobs', counts.nJobs, counts.nViews)
        "#{text1} #{text2}"
      else
        text1

    onSuccess: (json) ->
      counts = @jsonToCounts(json)
      text = @countsToText(counts)
      @$el.text(text)
      if counts.nJobs > 0
        @schedulePoll()

    onError: -> @schedulePoll()

  # The JobWatcher maintains the list of jobs, and it "moves" jobs to the
  # DocumentSet list when they're complete.
  #
  # The JobWatcher is a simple state machine. It cycles through the following
  # loop. Do not skip steps.
  #
  # * waiting: there's a setTimeout() waiting to poll the server
  # * polling: (through poll()): a request is sent to download the new jobs list
  # * receiving: (through receive_jobs()): we've updated the list. But the server
  #              doesn't send any info on jobs that are complete (they aren't in
  #              the server's new jobs list), so we've sent all those requests
  #              and are waiting for all the responses.
  #              @document_sets_to_receive > 0
  # * restart() is called when @document_sets_to_receive == 0. It either
  #             returns to waiting, or it dismantles the div.
  #
  # Any failed requests are retried ad infinitum.
  class JobWatcher
    constructor: (@jobs_div, @document_sets_ul) ->
      @refresh_url = @jobs_div.getAttribute('data-refresh-url')
      @document_set_url_pattern = @jobs_div.getAttribute('data-document-set-url-pattern')
      @restart()

    # Sets a poll timer if there are jobs we're monitoring.
    restart: () ->
      if $('li', @jobs_div).length
        @set_poll_timer(POLL_DELAY)

    set_poll_timer: (timeout) ->
      window.setTimeout(@poll.bind(this), timeout)

    poll: () ->
      $.getJSON(@refresh_url)
        .success(@receive_jobs.bind(this))
        .error(@set_poll_timer.bind(this, RETRY_DELAY))

    # Merges the JSON jobs into the DOM.
    #
    # Assumes the server always lists jobs in the same order.
    #
    # Calls _fetch_document_set(), which increments
    # @document_sets_to_receive as a side-effect.
    _merge_json_into_dom: (json) ->
      # json_index and $li are pointers to the JSON and DOM.
      json_index = 0
      $ul = $(@jobs_div).children('ul')
      $li = $ul.children('li:first')

      while json_index < json.length && $li.length
        json_id = json[json_index].id
        json_html = json[json_index].html

        dom_id = parseFloat($li.attr('data-document-set-id'))
        if json_id == dom_id
          # Update the DOM
          $new_li = $(json_html)
          $new_li.find('time').time_display()
          # Don't replace the entire element. That would replace the form; the
          # user's "Cancel import" clicks would sparodically disappear.
          #
          # Instead, replace the p.status, which is all that will ever change.
          $li.find('p.status').replaceWith($new_li.find('p.status'))
          # Advance
          $li = $li.next()
          json_index += 1
        else
          if $("li[data-document-set-id=#{json_id}]", @jobs_div).length
            # The $li we're looking at isn't in the JSON. That means the job
            # it represents is finished or deleted.
            @_fetch_document_set(dom_id)
            $li = $li.next()
          else
            # There's an extra JSON element that isn't in the DOM. This means
            # it's a new job.
            #
            # TODO: animate this? At the time of writing this comment, the only
            # way one would see such an animation would be to open two browser
            # tabs, add a job in one tab, and very quickly switch to the other
            # tab.
            $li.before(json_html)
            json_index += 1

      while json_index < json.length
        # There are more items at the bottom of the JSON
        $ul.append(json[json_index].html)
        json_index += 1
        # Don't alter $li. It's an empty element now, and we want that

      while $li.length
        # There are more items at the bottom of the DOM. They're either
        # finished or deleted.
        dom_id = parseFloat($li.attr('data-document-set-id'))
        @_fetch_document_set(dom_id)
        $li = $li.next()

      undefined

    _fetch_document_set: (id) ->
      @document_sets_to_receive += 1

      url = @document_set_url_pattern.replace('0', "#{id}")

      $.getJSON(url)
        .done(@_receive_document_set.bind(this))
        .fail (jqxhr) =>
          if (jqxhr.status == 403 || jqxhr.status == 404)
            # The document set is gone!
            # (403 Forbidden, 404 Not Found are semantically identical.)
            #
            # This means the docset was deleted.
            @_remove_document_set(id)
          else
            # Some other HTTP error occurred; let's retry
            setTimeout((=> @_fetch_document_set(id)), RETRY_DELAY)

    _remove_document_set: (id) ->
      $div = $(@jobs_div)
      $job_li = $div.find("li[data-document-set-id=#{id}]")

      done = =>
        @document_sets_to_receive -= 1
        @restart() if !@document_sets_to_receive

      # 1. Shrink @job_li to 0. Or, if it's the last job, shrink the entire
      #    div to 0.
      # 2. Delete the job (or the div)
      $job_li.slideUp ->
        nOtherJobs = $job_li.siblings().length
        if nOtherJobs
          $job_li.remove()
          done()
        else
          $div.slideUp ->
            $job_li.remove() # So we don't try and refresh it
            $div.remove()
            done()

    _receive_document_set: (json) ->
      $job_li = $("li[data-document-set-id=#{json.id}]", @jobs_div)
      $new_li = $(json.html)
      $new_li.find('time').time_display()
      $ul = $(@document_sets_ul)

      # Update $job_li with the latest HTML
      $new_job_li = $(json.html)
      $new_job_li.find('time').time_display()
      $job_li.replaceWith($new_job_li)
      $job_li = $new_job_li

      # We want it to look like the job is moving from the "in progress" list
      # to the "complete" list. These are the steps, which are taken in order.
      #
      # We assume:
      #
      # * $new_li and $job_li have the same outerHeight()
      #
      # 1. Prepend placeholder to $ul, and animate its height up
      # 2. Prepend $new_li to $ul, but with visibility: hidden
      # 3. Move $job_li to where $new_li is (with position: relative;)
      # 4. Make $new_li opaque and $job_li invisible
      # 5. Shrink $job_li to 0. Or, if it's the last job, shrink the entire
      #    div to 0.
      # 6. Delete the job (or the div).
      # 7. Restart if @document_sets_to_receive == 0

      $placeholder_li = $('<li class="placeholder"></li>')
      $placeholder_li.css({
        border: 'none'
        margin: 0
        padding: 0
        height: 0
      })
      $ul.prepend($placeholder_li)
      $div = $(@jobs_div)

      done = =>
        @document_sets_to_receive -= 1
        @restart() if !@document_sets_to_receive

      $new_li
        # 1. Animate placeholder
        # 3. Move $job_li over $new_li
        # 2. Insert $new_li, hidden (okay, we're kinda out of order here)
        # 4. Make $new_li opaque and $job_li invisible
        .queue ->
          $job_li.css({ position: 'relative' })
          final_placeholder_height = $job_li.outerHeight()
          start_offset = $job_li.offset()
          $job_li.animate({ width: $placeholder_li.width() }, {
            step: (now, fx) ->
              a = 1.0 - fx.pos
              b = fx.pos

              # We recalculate final_offset every frame, in case we're running
              # at the same time as other animations.
              final_offset = $placeholder_li.offset()
              step_offset = {
                top: a * start_offset.top + b * final_offset.top
                left: a * start_offset.left + b * final_offset.left
              }

              $placeholder_li.height(b * final_placeholder_height)
              $job_li.offset(step_offset)
            complete: ->
              $placeholder_li.replaceWith($new_li)
              $job_li.css({ visibility: 'hidden' })
              $new_li.dequeue()
          })
        # 5. Shrink job_li (or the whole div) to 0
        # 6. Delete the job (or the whole div)
        .queue ->
          # Turn off borders and padding, making job_li easier to animate
          $job_li.slideUp ->
            other_jobs_exist = $job_li.siblings().length
            if other_jobs_exist
              $job_li.remove()
              $new_li.dequeue()
            else
              $div.slideUp ->
                $job_li.remove() # so we don't restart()
                $div.remove()
                $new_li.dequeue()
        # 7. Restart if this was the last job removed
        .queue(done)

      $new_li.find('.view-count').each(maybeWatchReclustering)
      $(@document_sets_ul).next('p.no-document-sets').fadeOut(-> $(this).remove())

    receive_jobs: (json) ->
      @document_sets_to_receive = 0
      @_merge_json_into_dom(json) # might fire _receive_document_set(), decrementing @document_sets_to_receive and calling restart()
      @restart() if !@document_sets_to_receive

  maybeWatchReclustering = ->
    $el = $(@)
    nJobs = $el.attr('data-n-view-jobs')
    if nJobs != '0'
      documentSetId = $el.closest('[data-document-set-id]').attr('data-document-set-id')
      new ReclusterWatcher($el, documentSetId)

  $ ->
    document_sets = $('.document-sets>ul')[0]
    $('.document-set-creation-jobs').each ->
      jobs_div = this
      new JobWatcher(jobs_div, document_sets)

    $('li[data-document-set-id] .view-count[data-n-view-jobs]').each(maybeWatchReclustering)
