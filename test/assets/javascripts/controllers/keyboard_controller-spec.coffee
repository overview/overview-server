KeyboardController = require('controllers/keyboard_controller').KeyboardController

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

    expect_event = (controller_spec, method_spec) ->
      if !controllers[controller_spec]?
        controller = controllers[controller_spec] = {}
        keyboard_controller.add_controller(controller_spec, controller)
      controllers[controller_spec][method_spec] = (e) -> last_event = e

    press = (code, eventOptions=undefined) ->
      eventOptions = $.extend({}, eventOptions || {}, { which: code })
      e = jQuery.Event('keydown', eventOptions)
      $div.trigger(e)

    press_and_expect_event = (code, controller_spec, method_spec, eventOptions=undefined) ->
      expect_event(controller_spec, method_spec)
      press(code, eventOptions)
      expect(last_event).toBeDefined()
      expect(last_event.type).toEqual('keydown')

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

    it 'should throw an error if a controller exists but not its method', ->
      # assumes "j" maps to "DocumentList.go_down"
      keyboard_controller.add_controller('DocumentListController', {})
      expect(-> press('J'.charCodeAt(0))).toThrow('MethodNotFound')

    it 'should handle a keyboard shortcut with Control, using event.metaKey', ->
      # assumes "Control+A" maps to "DocumentList.select_all"
      press_and_expect_event('A'.charCodeAt(0), 'DocumentListController', 'select_all', { metaKey: true })
      expect(last_event.metaKey).toBe(true)

    it 'should handle a keyboard shortcut with Control, using event.ctrlKey', ->
      # assumes "Control+A" maps to "DocumentList.select_all"
      press_and_expect_event('A'.charCodeAt(0), 'DocumentListController', 'select_all', { ctrlKey: true })
      expect(last_event.ctrlKey).toBe(true)
