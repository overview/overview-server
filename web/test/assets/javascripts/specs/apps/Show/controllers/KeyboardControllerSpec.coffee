define [
  'underscore'
  'apps/Show/controllers/KeyboardController'
], (_, KeyboardController) ->
  describe 'controllers/KeyboardController', ->
    el = null

    beforeEach ->
      el = @el = document.createElement('div')
      @p = document.createElement('p')
      @input = document.createElement('input')
      @el.appendChild(@p)
      @el.appendChild(@input)
      document.body.appendChild(@el)
      @subject = new KeyboardController(@el)

    afterEach ->
      @subject.remove()
      document.body.removeChild(@el)

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
      el = eventOptions.el if eventOptions.el?
      el.dispatchEvent(e)

    it 'should not crash with unregistered keypresses', ->
      press('J'.charCodeAt(0))

    it 'should call a method with the event as argument', ->
      @subject.register(j: spy = sinon.spy())
      press('j'.charCodeAt(0))
      expect(spy).to.have.been.called
      e = spy.lastCall.args[0]
      expect(e.type).to.eq('keydown')

    it 'should call a method when the keypress comes on a child', ->
      @subject.register(j: spy = sinon.spy())
      press('j'.charCodeAt(0), el: @p)
      expect(spy).to.have.been.called

    it 'should not call a method when the keypress comes on an input', ->
      @subject.register(j: spy = sinon.spy())
      press('j'.charCodeAt(0), el: @input)
      expect(spy).not.to.have.been.called

    it 'should unregister a shortcut', ->
      @subject.register(j: spy = sinon.spy())
      @subject.unregister('j')
      press('j'.charCodeAt(0))
      expect(spy).not.to.have.been.called

    it 'should unregister a shortcut as part of an Array', ->
      @subject.register(j: spy = sinon.spy())
      @subject.unregister([ 'j' ])
      press('j'.charCodeAt(0))
      expect(spy).not.to.have.been.called

    it 'should unregister a shortcut as part of an Object', ->
      @subject.register(j: spy = sinon.spy())
      @subject.unregister(j: spy)
      press('j'.charCodeAt(0))
      expect(spy).not.to.have.been.called

    it 'should throw when double-registering', ->
      @subject.register(j: sinon.spy())
      expect(=> @subject.register(j: sinon.spy())).to.throw

    it 'should handle a keyboard shortcut with Control using event.metaKey', ->
      @subject.register('Control+a': spy = sinon.spy())
      press('a'.charCodeAt(0), metaKey: true)
      expect(spy).to.have.been.called

    it 'should handle a keyboard shortcut with Control using event.ctrlKey', ->
      @subject.register('Control+a': spy = sinon.spy())
      press('a'.charCodeAt(0), ctrlKey: true)
      expect(spy).to.have.been.called
