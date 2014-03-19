wd = require('wd')

class Registerer
  constructor: (@wd) ->
    @methods = null

  register: (methods) ->
    if @methods?
      throw 'You cannot register methods: other methods were already registered! This probably means another file forgot to call unregisterPromiseChainMethods().'

    @methods = methods
    for name, code of @methods
      @wd.addPromiseChainMethod(name, code)

    undefined

  unregister: ->
    # Do not throw an error if no methods were registered: we'd like to
    # encourage test authors to unregister all the time
    if @methods?
      for name, __ of @methods
        @wd.removeMethod(name)

    @methods = null

    undefined

module.exports =
  # Registers the given methods with the WD.js promise chain interface for
  # the duration of this describe block.
  #
  # Usage:
  #
  #     describe 'my test suite', ->
  #       usingPromiseChainMethods
  #         executeMoo:
  #           @execute('window.x = "moo";')
  usingPromiseChainMethods: (methods) ->
    registerer = new Registerer(wd)
    before -> registerer.register(methods)
    after -> registerer.unregister()
