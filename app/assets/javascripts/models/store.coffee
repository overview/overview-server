ObjectStore = require('models/object_store').ObjectStore

class Store
  constructor: (@node_store=undefined, @tag_store=undefined, @document_store=undefined) ->
    @node_store ||= new ObjectStore()
    @tag_store ||= new ObjectStore()
    @document_store ||= new ObjectStore()

exports = require.make_export_object('models/store')
exports.Store = Store
