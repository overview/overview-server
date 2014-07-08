define [ 'underscore' ], (_) ->
  INPUTS = # jQuery's :input, a zillion times faster
    INPUT: null
    SELECT: null
    TEXTAREA: null
    BUTTON: null

  A = 'A'.charCodeAt(0)
  Z = 'Z'.charCodeAt(0)
  a = 'a'.charCodeAt(0)
  z = 'z'.charCodeAt(0)

  # The dispatcher for "global" keyboard events.
  #
  # A "global" keyboard event is a keypress that occurs when the user isn't
  # typing text in. If the user is typing into a text field, the text field
  # should receive the input; otherwise, the user probably expects one of these
  # global events to fire.
  #
  # Use the KeyboardController like this:
  #
  #   keyboardController = new KeyboardController(document.body)
  #   keyboardController.register(
  #     x: -> doSomething()
  #     y: -> doSomethingToo()
  #   )
  #
  # Now when the user presses 'x', something will happen (on `keydown`).
  class KeyboardController
    constructor: (@el) ->
      @keyMap = {}

      @listener = @_onKeydown.bind(this)
      @el.addEventListener('keydown', @listener, false)

    # Stops listening forever.
    remove: ->
      @el.removeEventListener('keydown', @listener, false)

    register: (map) ->
      for k, v of map
        throw "There is already a callback mapped to `#{k}`" if @keyMap[k]?
        @keyMap[k] = v
      undefined

    unregister: (thing) ->
      if _.isString(thing)
        @keyMap[thing] = null
      if _.isArray(thing)
        @keyMap[k] = null for k in thing
      else # it's an Object
        @keyMap[k] = null for k, __ of thing
      undefined

    _onKeydown: (e) ->
      return if e.target.tagName of INPUTS

      i = e.keyCode || e.keyCodeX # keyCodeX is a hack for unit tests

      key = switch
        when a <= i <= z then String.fromCharCode(i)
        when A <= i <= Z then String.fromCharCode(i)
        when i == 8 then 'Backspace'
        when i == 9 then 'Tab'
        when i == 13 then 'Enter'
        when i == 27 then 'Escape'
        when i == 32 then 'Space'
        when i == 33 then 'PageUp'
        when i == 34 then 'PageDown'
        when i == 35 then 'End'
        when i == 36 then 'Home'
        when i == 37 then 'Left'
        when i == 38 then 'Up'
        when i == 39 then 'Right'
        when i == 40 then 'Down'
        when i == 45 then 'Insert'
        when i == 46 then 'Delete'
        when i == 191 then 'Slash'
        else null

      if key
        meta = e.metaKey || e.ctrlKey || false
        full_key = "#{meta && 'Control+' || ''}#{e.shiftKey && 'Shift+' || ''}#{key}"

        callback = @keyMap[full_key]
        if callback
          callback(e)
          e.stopPropagation()
          e.preventDefault()
