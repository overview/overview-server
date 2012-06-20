class Selection
  constructor: () ->
    @nodes = []
    @tags = []
    @documents = []
    @callbacks = []

  update: (options) ->
    if options.nodes?
      this._update_one('nodes', options.nodes)
    else if options.node?
      this._update_one('nodes', [options.node])

    if options.tags?
      this._update_one('tags', options.tags)
    else if options.tag?
      this._update_one('tags', [options.tag])

    if options.documents?
      this._update_one('documents', options.documents)
    else if options.document?
      this._update_one('documents', [options.document])

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
    this._changed()

  on_change: (callback) ->
    @callbacks.push(callback)

  _changed: () ->
    for callback in @callbacks
      callback()

exports = require.make_export_object('models/selection')
exports.Selection = Selection
