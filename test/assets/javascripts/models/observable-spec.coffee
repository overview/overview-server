observable = require('models/observable').observable

describe 'models/', ->
  describe 'observable', ->
    it 'should add "observe", "unobserve" and "_notify" methods', ->
      class O
        observable(this)

      o = new O()
      expect(o.observe).toBeDefined()
      expect(o.unobserve).toBeDefined()
      expect(o._notify).toBeDefined()

    it 'should not crash in _notify() if there are no callbacks', ->
      class O
        observable(this)

      o = new O()
      o._notify('event')

    it 'should notify an observer', ->
      class O
        observable(this)

      x = 0
      o = new O()
      o.observe('event', -> x = 1)
      o._notify('event')
      expect(x).toEqual(1)

    it 'should not notify when a different event happens', ->
      class O
        observable(this)

      x = 0
      o = new O()
      o.observe('event', -> x = 1)
      o._notify('other-event')
      expect(x).toEqual(0)

    it 'should unobserve a callback', ->
      class O
        observable(this)

      x = 0
      y = 0
      callback = () -> x += 1
      callback2 = () -> y += 1
      o = new O()
      o.observe('event', callback)
      o.observe('event', callback2)
      o.unobserve('event', callback)
      o._notify('event')
      expect(x).toEqual(0)
      expect(y).toEqual(1)

    it 'should pass the object as "this" in the callback', ->
      class O
        observable(this)

      _this = undefined
      o = new O()
      o.observe('event', -> _this = this)
      o._notify('event')
      expect(_this).toBe(o)

    it 'should work simplified (that is, with no events)', ->
      class O
        observable(this)

      x = 0
      o = new O()
      o.observe(-> x = 1)
      o._notify()
      expect(x).toEqual(1)

    it 'should pass an argument in the callback', ->
      class O
        observable(this)

      _arg = undefined
      o = new O()
      o.observe('event', (arg) -> _arg = arg)
      o._notify('event', 'foo')
      expect(_arg).toEqual('foo')

    it 'should pass multiple arguments in the callback', ->
      class O
        observable(this)

      a1 = undefined
      a2 = undefined
      o = new O()
      o.observe('event', (arg1, arg2) -> a1 = arg1; a2 = arg2)
      o._notify('event', 1, 2)
      expect(a1).toEqual(1)
      expect(a2).toEqual(2)
