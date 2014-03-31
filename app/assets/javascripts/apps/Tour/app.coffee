define [
  'underscore'
  'jquery'
  'vow'
  'i18n'
  'bootstrap-tooltip'
  'bootstrap-popover'
], (_, $, vow, i18n) ->
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
    constructor: (@tour) ->
      @_doneDeferred = vow.defer()
      @_donePromise = @_doneDeferred
        .promise()
        .then(=> @_disableTooltipsOnServer())

      @listen()
      @start()

    template: _.template("""
      <div class="content"><%= html %></div>
      <div class="actions">
        <span class="tip-number"><%- t('tipNumber', step, nSteps) %></span>
        <% if (!isFirst) { %>
          <a class="previous" href="#"><i class="overview-icon-chevron-left"></i> <%- t('previous') %></a>
        <% } %>
        <% if (isLast) { %>
          <a class="done" href="#"><%- t('done') %></a>
        <% } else { %>
          <a class="next" href="#"><%- t('next') %> <i class="overview-icon-chevron-right"></i></a>
          <a class="skip" href="#"><%- t('skip') %></a>
        <% } %>
      </div>
    """)

    # Returns a Promise
    _disableTooltipsOnServer: ->
      vow.when($.ajax(type: 'DELETE', url: DoneUrl))

    # Returns a jQuery object representing the thing _being_ popped over
    _buildPopover: ->
      options = @tour[@step]

      throw 'Must supply `find`, a jQuery selector' if !options.find?
      throw 'Must supply `title`, a String' if !options.title?
      throw 'Must supply `bodyHtml`, an HTML String' if !options.bodyHtml?

      html = @template
        html: options.bodyHtml
        isFirst: (@step == 0)
        isLast: (@step == @tour.length - 1)
        step: @step + 1
        nSteps: @tour.length
        t: t

      $el = $(options.find)
        .popover
          html: true
          placement: 'auto'
          trigger: 'manual'
          title: options.title
          content: html
          container: 'body'
          placement: options.placement
        .popover('show')

    # Give this the return value from _buildPopover()
    _destroyPopover: ($el) ->
      $el
        .off('.tour')
        .popover('destroy')

    start: ->
      @step = 0
      @$popover = @_buildPopover()

    donePromise: -> @_donePromise
    done: ->
      @remove()
      @_doneDeferred.resolve(@)

    _onNext: (e) ->
      e.preventDefault()
      @next()

    _leaveStep: ->
      if @$popover?
        @_destroyPopover(@$popover)
      @$popover = null

    _enterStep: (@step) ->
      @$popover = @_buildPopover()

    _goToStep: (step) ->
      @_leaveStep()
      @_enterStep(step)

    next: -> @_goToStep(@step + 1)
    previous: -> @_goToStep(@step - 1)

    listen: ->
      $('body')
        .on('click.tour', '.popover a.next', ((e) => e.preventDefault(); @next()))
        .on('click.tour', '.popover a.previous', ((e) => e.preventDefault(); @previous()))
        .on('click.tour', '.popover a.done, .popover a.skip', ((e) => e.preventDefault(); @done()))

    remove: ->
      @_destroyPopover(@$popover) if @$popover?
      $('body').off('.tour')
