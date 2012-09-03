observable = require('models/observable').observable
Selection = require('models/selection').Selection

PROPERTIES = [ 'selection' ]

class State
  observable(this)

  constructor: () ->
    @selection = new Selection()
    @focused_tag = undefined

  set: (property, value) ->
    old_value = this[property]

    # Check for equality
    return if old_value is value
    return if old_value? && value? && old_value.equals? && old_value.equals(value)

    this[property] = value
    this._notify("#{property}-changed", value)

exports = require.make_export_object('models/state')
exports.State = State
