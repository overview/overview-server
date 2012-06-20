class ObjectStore
  constructor: () ->
    @objects = {}
    @_counts = {}

  add: (object) ->
    objectid = object.id
    if @_counts[objectid]?
      @_counts[objectid]++
    else
      @objects[object.id] = object
      @_counts[objectid] = 1

  get: (objectid) ->
    @objects[objectid]

  remove: (object) ->
    objectid = object.id

    @_counts[objectid]--
    if @_counts[objectid] == 0
      delete @_counts[objectid]
      delete @objects[objectid]

exports = require.make_export_object('models/object_store')
exports.ObjectStore = ObjectStore
