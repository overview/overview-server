define ->
  INPUTS = # jQuery's :input, a zillion times faster
    INPUT: null
    SELECT: null
    TEXTAREA: null
    BUTTON: null

  # Each event looks like: "key": "controller.method" ]
  #
  # Key looks like: 'a' or 'Control+a' or 'Control+Shift+a' (only one key
  #                 allowed; duplicates must be copied; "Control" comes
  #                 before "Shift")
  # Controller is CamelCase
  # method will be called with the keyboard event as its only argument
  #
  # Special keys: Enter, Escape, Up, Down, Left, Right, PageUp, PageDown,
  # Home, End, Tab, Space, Insert, Delete, Backspace, Slash.
  #
  # This list must be sorted alphabetically. This helps us avoid duplicates.
  EVENTS = {
    'Control+a': 'DocumentListController.select_all'
    'Down': 'TreeController.go_down'
    'Left': 'TreeController.go_left'
    'PageDown': 'DocumentContentsController.page_down'
    'PageUp': 'DocumentContentsController.page_up' # can't work for iframes
    'Right': 'TreeController.go_right'
    'Up': 'TreeController.go_up'
    'a': 'TreeController.go_left'
    'd': 'TreeController.go_right'
    'j': 'DocumentListController.go_down' # GMail
    'k': 'DocumentListController.go_up' #GMail
    's': 'TreeController.go_down'
    'u': 'DocumentListController.select_all' # GMail
    'w': 'TreeController.go_up'
  }

  # The dispatcher for "global" keyboard events.
  #
  # A "global" keyboard event is a keypress that occurs when the user isn't
  # typing text in. If the user is typing into a text field, the text field
  # should receive the input; otherwise, the user probably expects one of these
  # global events to fire.
  #
  # Use the KeyboardController like this:
  #
  # keyboardController = new KeyboardController(document.body)
  # otherController = new OtherController(...)
  # keyboardController.add_controller('OtherController', otherController)
  #
  # The KeyboardController holds the logic for each controller. This is so we
  # don't write conflicting rules.
  class KeyboardController
    constructor: (@el) ->
      @controllers = {}
      @listener = @handle_keydown.bind(this)

      @el.addEventListener('keydown', @listener, false)

    remove: ->
      @el.removeEventListener('keydown', @listener, false)

    add_controller: (spec, controller) ->
      @controllers[spec] = controller

    handle_keydown: (e) ->
      return if e.target.tagName of INPUTS

      i = e.keyCode || e.keyCodeX # keyCodeX is a hack for unit tests

      A = 'A'.charCodeAt(0)
      Z = 'Z'.charCodeAt(0)
      a = 'a'.charCodeAt(0)
      z = 'z'.charCodeAt(0)

      key = switch
        when A <= i <= Z then String.fromCharCode(i).toLowerCase()
        when a <= i <= z then String.fromCharCode(i)
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

      if key?
        meta = e.metaKey || e.ctrlKey || false
        full_key = "#{meta && 'Control+' || ''}#{e.shiftKey && 'Shift+' || ''}#{key}"

        spec = EVENTS[full_key]
        if spec?
          [ controller_spec, method_spec ] = spec.split('.')

          controller = @controllers[controller_spec]

          if controller?
            throw 'MethodNotFound' if method_spec not of controller
            controller[method_spec].call({}, e)
            e.stopPropagation()
            e.preventDefault()
