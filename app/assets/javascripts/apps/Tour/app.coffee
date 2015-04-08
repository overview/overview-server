define [
  'underscore'
  'jquery'
  'rsvp'
  'i18n'
], (_, $, rsvp, i18n) ->
  t = i18n.namespaced('views.Tree.show.Tour')

  DoneUrl = '/tour'

  # Displays a bunch of popovers describing the app.
  #
  # Call it like this:
  #
  #   app = new TourApp [
  #     { find: '#el1', title: 'Step 1', bodyHtml: '<p>this is something</p>' }
  #     ...
  #   ]
  #
  #   done = app.donePromise() # resolved when user is done
  class TourApp
    constructor: (tour, options) ->
      @options = options
      @$container = if options?.container
        $(options.container)
      else
        $('body')

      @tour = @_parseTour(tour)

      @_doneDeferred = rsvp.defer()
      @_donePromise = @_doneDeferred
        .promise
        .then(=> @_disableTooltipsOnServer())

      @listen()
      @start()

    template: _.template("""
      <div class="popover fade in <%- placement %>">
        <div class="arrow"></div>
        <div class="popover-title"><%- title %></div>
        <div class="popover-content">
          <div class="content"><%= html %></div>
          <div class="actions">
            <span class="tip-number"><%- t('tipNumber', step, nSteps) %></span>
            <% if (!isFirst) { %>
              <a class="previous" href="#"><i class="icon overview-icon-chevron-left"></i> <%- t('previous') %></a>
            <% } %>
            <% if (isLast) { %>
              <a class="done" href="#"><%- t('done') %></a>
            <% } else { %>
              <a class="next" href="#"><%- t('next') %> <i class="icon overview-icon-chevron-right"></i></a>
              <a class="skip" href="#"><%- t('skip') %></a>
            <% } %>
          </div>
        </div>
      </div>
    """)

    # Returns a Promise
    _disableTooltipsOnServer: ->
      rsvp.resolve($.ajax(
        type: 'DELETE'
        url: DoneUrl
      ))

    _parseTour: (input) ->
      outputPlusNull = for item in input
        throw 'Must supply `find`, a jQuery selector' if !item.find?
        throw 'Must supply `title`, a String' if !item.title?
        throw 'Must supply `bodyHtml`, an HTML String' if !item.bodyHtml?

        el = @$container.find(item.find).get(0)

        if el
          el: el
          title: item.title
          bodyHtml: item.bodyHtml
          placement: item.placement
        else
          null

      for item in outputPlusNull when item?
        item

    # Returns a jQuery object representing the thing _being_ popped over
    #
    # We use Twitter Bootstrap's CSS styles, but we _don't_ use their
    # JavaScript. This is faster.
    _buildPopover: ->
      options = @tour[@step]

      html = @template
        placement: options.placement
        title: options.title
        html: options.bodyHtml
        isFirst: (@step == 0)
        isLast: (@step == @tour.length - 1)
        step: @step + 1
        nSteps: @tour.length
        t: t

      $popover = $(html)
        .appendTo(@$container)

      $el = $(options.el)
      if !@options?.skipRepaint
        $popover.show() # so we can find its position

        # We put all these together to avoid an excess repaint
        elRect = $el[0].getBoundingClientRect()
        tipWidth = $popover[0].offsetWidth
        tipHeight = $popover[0].offsetHeight

        positionCss = switch options.placement
          when 'left'
            top: (elRect.top + elRect.bottom) * 0.5 - tipHeight * 0.5
            left: elRect.left - tipWidth
          when 'right'
            top: (elRect.top + elRect.bottom) * 0.5 - tipHeight * 0.5
            left: elRect.right
          when 'top'
            top: elRect.top - tipHeight
            left: (elRect.left + elRect.right) * 0.5 - (tipWidth * 0.5)
          when 'bottom'
            top: elRect.bottom
            left: (elRect.left + elRect.right) * 0.5 - (tipWidth * 0.5)
          else throw "Invalid placement #{options.placement}"

        $popover
          .css(positionCss)

      $popover

    start: ->
      @step = 0
      @$popover = @_buildPopover()

    donePromise: -> @_donePromise
    done: ->
      @_doneDeferred.resolve(@)
      @remove()
      @_donePromise

    _onNext: (e) ->
      e.preventDefault()
      @next()

    _leaveStep: ->
      @$popover?.remove()
      @$popover = null

    _enterStep: (@step) ->
      @$popover = @_buildPopover()

    _goToStep: (step) ->
      @_leaveStep()
      @_enterStep(step)

    next: -> @_goToStep(@step + 1)
    previous: -> @_goToStep(@step - 1)

    listen: ->
      @$container
        .on('click.tour', '.popover a.next', ((e) => e.preventDefault(); @next()))
        .on('click.tour', '.popover a.previous', ((e) => e.preventDefault(); @previous()))
        .on('click.tour', '.popover a.done, .popover a.skip', ((e) => e.preventDefault(); @done()))

    remove: ->
      @$popover?.remove()
      @$container.off('.tour')
      @_doneDeferred.reject(null) # presumably, it already resolved, so this is a no-op except in tests
      undefined
