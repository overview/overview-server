class Document
  constructor: (id, properties) ->
    this.id = id
    for property, value of properties
      this[property] = value

exports = require.make_export_object('models/document')
exports.Document = Document
