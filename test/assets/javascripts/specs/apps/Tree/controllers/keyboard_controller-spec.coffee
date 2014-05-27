define [
  'underscore'
  'apps/Tree/controllers/keyboard_controller'
], (_, KeyboardController) ->
  # This test makes a few assumptions about what keyboard shortcuts
  # KeyboardController routes to. Search for "# assume" to see them.
  describe 'controllers/keyboard_controller', ->
    describe 'KeyboardController', ->
      $div = undefined
      keyboard_controller = undefined
      controllers = undefined
      last_event = undefined

      beforeEach ->
        $div = $('<div></div>')
        keyboard_controller = new KeyboardController($div[0])
        last_event = undefined
        controllers = {}

      afterEach ->
        keyboard_controller.remove()

      expect_event = (controller_spec, method_spec) ->
        if !controllers[controller_spec]?
          controller = controllers[controller_spec] = {}
          keyboard_controller.add_controller(controller_spec, controller)
        controllers[controller_spec][method_spec] = (e) -> last_event = e

      press = (code, eventOptions={}) ->
        o = 
          location: null
          view: null
          bubbles: true
          cancelable: true
          detail: code
          keyIdentifier: String.fromCharCode(code)
          ctrlKey: !!eventOptions.ctrlKey
          shiftKey: !!eventOptions.shiftKey
          altKey: !!eventOptions.altKey
          metaKey: !!eventOptions.metaKey

        # XXX This only works with PhantomJS...
        e = document.createEvent("KeyboardEvent")
        e.initKeyboardEvent(
          "keydown", 
          o.bubbles,
          o.cancelable,
          o.view,
          o.keyIdentifier,
          o.keyLocation,
          o.ctrlKey,
          o.altKey,
          o.shiftKey,
          o.metaKey,
          false
        )
        e.keyCodeX = code # e.keyCode is read-only
        $div[0].dispatchEvent(e)

      press_and_expect_event = (code, controller_spec, method_spec, eventOptions=undefined) ->
        expect_event(controller_spec, method_spec)
        press(code, eventOptions)
        expect(last_event).not.to.be.undefined
        expect(last_event.type).to.eq('keydown')

      it 'should not crash with unregistered keypresses', ->
        # assume "\" is not used
        press(220)

      it 'should not crash when a controller is unregistered', ->
        # assume "j" is used
        press('J'.charCodeAt(0))

      it 'should call a method with the event as argument', ->
        # assume "j" maps to "DocumentList.go_down"
        press_and_expect_event('J'.charCodeAt(0), 'DocumentListController', 'go_down')

      it 'should pass the event to the method', ->
        # assume "j" maps to "DocumentList.go_down"
        press_and_expect_event('J'.charCodeAt(0), 'DocumentListController', 'go_down')

      # Commented out: when using dispatchEvent, this error seems to be
      # uncatchable. This shouldn't be a problem in production.
      xit 'should throw an error if a controller exists but not its method', ->
        # assumes "j" maps to "DocumentList.go_down"
        keyboard_controller.add_controller('DocumentListController', {})
        expect(-> press('J'.charCodeAt(0))).to.throw('MethodNotFound')

      it 'should handle a keyboard shortcut with Control using event.metaKey', ->
        # assumes "Control+A" maps to "DocumentList.select_all"
        press_and_expect_event('A'.charCodeAt(0), 'DocumentListController', 'select_all', { metaKey: true })
        expect(last_event.metaKey).to.be(true)

      it 'should handle a keyboard shortcut with Control using event.ctrlKey', ->
        # assumes "Control+A" maps to "DocumentList.select_all"
        press_and_expect_event('A'.charCodeAt(0), 'DocumentListController', 'select_all', { ctrlKey: true })
        expect(last_event.ctrlKey).to.be(true)
