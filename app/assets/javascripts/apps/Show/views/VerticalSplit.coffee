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
    initialize: ->
      throw 'Must set options.el, an HTMLElement' if !@options.el
      throw 'Must set options.storage, a Storage' if !@options.storage
      throw 'Must set options.storageKey, a String' if !@options.storageKey

      @storage = @options.storage
      @storageKey = @options.storageKey

      @render()

      @listener = (ev) => @startResize(ev)
      @el.childNodes[1].addEventListener('mousedown', @listener)

    remove: ->
      @el.childNodes[1].removeEventListener('mousedown', @listener)
      Backbone.View.prototype.remove.call(@)

    get: -> @storage.getItem(@storageKey)
    set: (valueString) -> @storage.setItem(@storageKey, valueString)

    render: ->
      @$el.children(':eq(0)').css('width', @get())
      @$el.children(':eq(2)').css('width', (100 - parseFloat(@get())) + '%')

    startResize: (ev) ->
      ev.preventDefault() # prevent text selection

      width1 = @el.childNodes[0].offsetWidth
      splitWidth = @el.childNodes[1].offsetWidth
      fullWidth = @el.offsetWidth
      x1 = ev.pageX

      # If there's an iframe on the page, it'll grab mousemove events. Put
      # something on top to silence events for all other parts of the page.
      overlay = document.createElement('div')
      overlay.style.position = 'fixed'
      overlay.style.left = 0
      overlay.style.right = 0
      overlay.style.width = '100%'
      overlay.style.height = '100%'
      overlay.style.zIndex = Number.MAX_SAFE_INTEGER
      document.body.appendChild(overlay)

      onMouseMove = (ev) =>
        x2 = ev.pageX
        width2 = width1 + x2 - x1
        percent = 100 * width2 / fullWidth
        percent = Math.min(70, Math.max(30, percent))
        Backbone.$(window).trigger('resize') # TODO specifically target tree+plugins, nothing else
        @set(percent + '%')
        @render()

      onMouseUp = (ev) ->
        onMouseMove(ev)
        document.body.removeChild(overlay)
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)

      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
