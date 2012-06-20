observable = require('models/observable').observable

class Selection
  observable(this)

  constructor: () ->
    @nodes = []
    @tags = []
    @documents = []

  update: (options) ->
    changed1 = if options.nodes?
      this._update_one('nodes', options.nodes)
    else if options.node?
      this._update_one('nodes', [options.node])

    changed2 = if options.tags?
      this._update_one('tags', options.tags)
    else if options.tag?
      this._update_one('tags', [options.tag])

    changed3 = if options.documents?
      this._update_one('documents', options.documents)
    else if options.document?
      this._update_one('documents', [options.document])

    this._notify() if changed1 || changed2 || changed3

  _update_one: (key, new_value) ->
    old_value = this[key]
    new_value ||= []

    equal = (old_value.length == new_value.length)
    if equal
      for x in old_value
        found = false
        for y in new_value
          if (x.id? && x.id || x) == (y.id? && y.id || y)
            found = true
            break
        if !found
          equal = false
          break

    return if equal

    this[key] = new_value

exports = require.make_export_object('models/selection')
exports.Selection = Selection
