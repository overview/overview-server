define [ 'jquery', 'backbone' ], ($, Backbone) ->
  Backbone.View.extend
    options:
      handleWidth: 11 # px
      borderWidth: 0 # px

    events:
      'mousedown .middle': '_onMousedownMiddle'
      'mousedown .handle': '_onMousedownHandle'

    initialize: (options) ->
      throw 'Must pass options.model, an AnimatedFocus' if !@model
      @initialRender()
      @render()

      @listenTo(@model, 'change', => @render())
      $(window).on('resize.focus-view', => @render())

    initialRender: ->
      @$el.html("""
        <div class="line"/><div class="handle left"/><div class="middle"/><div class="handle right"/>
      """)
      @$el.children('.line').css
        position: 'absolute'
        left: 0
        right: 0

      @$el.children('.handle, .middle').css
        position: 'absolute'
        top: 0
        bottom: 0
        width: "#{@options.handleWidth}px"

      @$handles =
        left: @$el.children('.left')
        middle: @$el.children('.middle')
        right: @$el.children('.right')

    render: ->
      for el, css of @_getXsAndWidths()
        @$handles[el].css(css)
      this

    remove: ->
      $(document).off('.focus-view')
      $(window).off('.focus-view')
      @$overlayPane?.remove()
      Backbone.View.prototype.remove.call(this)

    _onMousedownMiddle: (e) ->
      return if e.which != 1
      e.preventDefault()

      x1 = e.pageX
      width = @$el.width()
      pan1 = @model.get('pan')

      update = (e) =>
        dx = e.pageX - x1
        dPan = dx / width
        pan = pan1 + dPan
        @trigger('zoom-pan', { zoom: @model.get('zoom'), pan: pan })

      @$overlayPane = $('<div id="focus-view-mousemove-handler"/>').appendTo($('body'))

      $(document).on 'mousemove.focus-view', (e) ->
        update(e)
        e.preventDefault()

      $(document).on 'mouseup.focus-view', (e) =>
        update(e)
        @$overlayPane?.remove()
        @$overlayPane = undefined
        $(document).off('.focus-view')
        e.preventDefault()

    _onMousedownHandle: (e) ->
      return if e.which != 1
      e.preventDefault()

      leftOrRight = $(e.target).hasClass('left') && 'left' || 'right'
      xsAndWidths = this._getXsAndWidths()
      positions = {
        left: xsAndWidths.middle.left
        right: xsAndWidths.middle.left + xsAndWidths.middle.width
      }
      startX = e.pageX
      width = @$el.width()

      update = (e) =>
        dx = e.pageX - startX
        thisX = positions[leftOrRight] + dx
        otherX = leftOrRight == 'left' && positions.right || positions.left

        x1 = if otherX < thisX then otherX else thisX
        x2 = if otherX < thisX then thisX else otherX

        zoom = (x2 - x1) / width # difference
        pan = ((x1 / width - 0.5) + (x2 / width) - 0.5) / 2 # average

        @trigger('zoom-pan', { zoom: zoom, pan: pan })

      @$overlayPane = $('<div id="focus-view-mousemove-handler"/>').appendTo($('body'))

      $(document).on 'mousemove.focus-view', (e) ->
        update(e)
        e.preventDefault()
      $(document).on 'mouseup.focus-view', (e) =>
        update(e)
        @$overlayPane?.remove()
        @$overlayPane = undefined
        $(document).off('.focus-view')
        e.preventDefault()

    # Returns { left: { left: px, width: px }, middle: {}, right: {} }
    #
    # The position:absolute handles should be positioned according to these
    # numbers.
    #
    # There are a few guarantees:
    # * "left" and "right" width will == @options.handleWidth
    # * "middle" width will be >= @options.handleWidth
    # * "left" and "right" may overlap "middle", but they will always leave
    #   @options.handleWidth pixels *not* overlapped.
    _getXsAndWidths: () ->
      zoom = @model.get('zoom')
      pan = @model.get('pan')

      width = @el.clientWidth || @$el.width() # first is subpixel-perfect; the second is for tests

      ret = {
        middle: {
          left: (0.5 + pan - zoom * 0.5) * width
          width: zoom * width
        }
      }

      if ret.middle.width < @options.handleWidth
        handleWidth_missing = @options.handleWidth - ret.middle.width
        ret.middle.left -= handleWidth_missing * 0.5
        ret.middle.width = @options.handleWidth

      ret.left = {
        left: ret.middle.left - @options.handleWidth * 0.5
        width: @options.handleWidth
      }

      ret.right = {
        left: ret.middle.left + ret.middle.width - @options.handleWidth * 0.5
        width: @options.handleWidth
      }

      if ret.left.left < 0
        ret.left.left = 0

      if ret.right.left + ret.right.width > width
        ret.right.left = width - ret.right.width

      spaceBetween = ret.right.left - (ret.left.left + ret.left.width)
      if spaceBetween < @options.handleWidth
        diff = @options.handleWidth - spaceBetween
        ret.left.left -= diff * 0.5
        ret.right.left += diff * 0.5

      ret.middle.left -= @options.borderWidth
      ret.middle.width += 2 * @options.borderWidth

      ret
