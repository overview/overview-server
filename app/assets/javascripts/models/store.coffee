ObjectStore = require('models/object_store').ObjectStore

class Store
  constructor: (@nodes=undefined, @tags=undefined, @documents=undefined) ->
    @nodes ||= new ObjectStore()
    @tags ||= new ObjectStore()
    @documents ||= new ObjectStore()

exports = require.make_export_object('models/store')
exports.Store = Store
