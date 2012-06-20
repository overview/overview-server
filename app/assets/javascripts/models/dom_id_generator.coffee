class DomIdGenerator
  constructor: (@base) ->
    @_next = 1

  next: () ->
    n = @_next
    @_next += 1

    "#{@base}-#{n}"

  nodeToGuaranteedDomId: (node) ->
    node.id ||= this.next()

exports = require.make_export_object('models/dom_id_generator')
exports.DomIdGenerator = DomIdGenerator
