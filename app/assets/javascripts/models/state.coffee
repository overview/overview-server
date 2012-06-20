observable = require('models/observable').observable
Selection = require('models/selection').Selection

PROPERTIES = []

class State
  observable(this)

  constructor: () ->
    @properties = {}
    @selection = new Selection()

    @selection.observe => this._notify('selection')

    for property in PROPERTIES
      @properties[property] = undefined

  get: (property) ->
    @properties[property]

  set: (property, value) ->
    @oldValue = this.get(property)

    # Check for equality
    return if @oldValue is value
    return if @oldValue? && value? && @oldValue.equals? && @oldValue.equals(value)

    @properties[property] = value
    this._notify(property)

exports = require.make_export_object('models/state')
exports.State = State
