define [ 'backbone' ], (Backbone) ->
  # Sets and restores the width% of the first element in `el` using
  # `localStorage`.
  #
  # Usage:
  #
  #   new VerticalSplit({
  #     el: $('#main')[0],
  #     storage: window.localStorage,
  #     storageKey: 'my-storage-key'
  #   })
  Backbone.View.extend
    initialize: (options) ->
      throw 'Must set options.el, an HTMLElement' if !options.el
      throw 'Must set options.storage, a Storage' if !options.storage
      throw 'Must set options.storageKey, a String' if !options.storageKey

      @storage = options.storage
      @storageKey = options.storageKey

      @render()

      @mouseDownListener = (ev) => @startResize(ev)
      @clickListener = (ev) => @onClickButton(ev)
      @el.childNodes[1].addEventListener('mousedown', @mouseDownListener)
      @el.childNodes[3].addEventListener('mousedown', @mouseDownListener)
      @el.childNodes[1].childNodes[0].addEventListener('click', @clickListener)
      @el.childNodes[3].childNodes[0].addEventListener('click', @clickListener)

    remove: ->
      @el.childNodes[1].removeEventListener('mousedown', @mouseDownListener)
      @el.childNodes[3].addEventListener('mousedown', @mouseDownListener)
      @el.childNodes[1].childNodes[0].removeEventListener('click', @clickListener)
      @el.childNodes[3].childNodes[0].removeEventListener('click', @clickListener)
      Backbone.View.prototype.remove.call(@)

    get: (subkey) -> @storage.getItem("#{@storageKey}.#{subkey}")
    set: (subkey, valueString) -> @storage.setItem("#{@storageKey}.#{subkey}", valueString)

    render: ->
      # The logic is suuuuper-simple. We'll need to revisit this.
      #
      # There are five HTML elements:
      #
      #  left  mid  right
      #   v     v     v
      # +----+-----+----+
      # |    |     |    |
      # |    |     |    |
      # |    |     |    |
      # +----+-----+----+
      #      ^     ^
      #   split1 split2
      #
      # Elsewhere, JavaScript gives the parent element the class "has-right-pane" when
      # a right pane is added and removes the class when the right pane is removed.
      #
      # The other important CSS class is "on-right-pane", which decides whether we're
      # focusing on left+mid or on mid+right.
      #
      # If has-right-pane:
      # * split1 and split2 will resize the middle element. (You can't resize the
      #   side elements: they always are 100% minus the middle element's size.)
      # * if on-right-pane, left has width 0
      # * if on-left-pane, right has width 0

      middleWidth = parseFloat(@get('middleWidthPercent') || '50')
      sideWidth = 100 - middleWidth

      if @el.classList.contains('has-right-pane')
        if @el.classList.contains('on-right-pane')
          leftWidth = 0
          rightWidth = sideWidth
        else
          leftWidth = sideWidth
          rightWidth = 0
      else
        leftWidth = sideWidth
        rightWidth = 0
        # Since _we_ set the class "on-right-pane", it's our responsibility to
        # remove it. If we don't, we'll scroll automatically the next time a
        # right pane appears.
        @el.classList.remove('on-right-pane')

      @el.childNodes[0].style.width = leftWidth + '%'
      @el.childNodes[2].style.width = middleWidth + '%'
      @el.childNodes[4].style.width = rightWidth + '%'

    onClickButton: (ev) ->
      return if !@el.classList.contains('has-right-pane')
      ev.stopPropagation()
      ev.preventDefault()

      @el.classList.toggle('on-right-pane')
      @render()

    startResize: (ev) ->
      ev.preventDefault() # prevent text selection

      # Clicking a button should not resize
      return if ev.target.nodeName == 'BUTTON'

      onRightPane = @el.classList.contains('on-right-pane')

      # You can only resize using the middle split. Return if we're
      # resizing the other one.
      return if (ev.target == @el.childNodes[1]) == onRightPane

      @el.classList.add('resizing')

      width1 = @el.childNodes[2].offsetWidth
      splitEl = onRightPane && @el.childNodes[3] || @el.childNodes[1]
      splitWidth = splitEl.offsetWidth
      fullWidth = @el.offsetWidth
      x1 = ev.pageX

      # If there's an iframe on the page, it'll grab mousemove events. Put
      # something on top to silence events for all other parts of the page.
      overlay = document.createElement('div')
      overlay.className = 'vertical-split-mousemove-trap'
      overlay.style.position = 'fixed'
      overlay.style.left = 0
      overlay.style.right = 0
      overlay.style.width = '100%'
      overlay.style.height = '100%'
      overlay.style.cursor = 'ew-resize'
      overlay.style.zIndex = Number.MAX_SAFE_INTEGER
      document.body.appendChild(overlay)

      onMouseMove = (ev) =>
        x2 = ev.pageX
        dx = onRightPane && x2 - x1 || x1 - x2
        width2 = width1 + dx
        percent = 100 * width2 / fullWidth
        percent = Math.min(70, Math.max(30, percent))
        Backbone.$(window).trigger('resize') # TODO specifically target tree+plugins, nothing else
        @set('middleWidthPercent', percent + '%')
        @render()

      onMouseUp = (ev) =>
        onMouseMove(ev)
        document.body.removeChild(overlay)
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)
        @el.classList.remove('resizing')

      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
