Selection = require('models/selection').Selection

PROPERTIES = []

class State
  constructor: () ->
    @properties = {}
    @callbacks = {}
    @selection = new Selection()

    @selection.on_change () =>
      this._changed('selection')
    @callbacks.selection = []

    for property in PROPERTIES
      @properties[property] = undefined
      @callbacks[property] = []

  get: (property) ->
    @properties[property]

  set: (property, value) ->
    @oldValue = this.get(property)

    # Check for equality
    return if @oldValue is value
    return if @oldValue? && value? && @oldValue.equals? && @oldValue.equals(value)

    @properties[property] = value
    this._changed(property)

  on_change: (property, callback) ->
    @callbacks[property].push(callback)

  _changed: (property) ->
    for callback in @callbacks[property]
      callback()

exports = require.make_export_object('models/state')
exports.State = State
