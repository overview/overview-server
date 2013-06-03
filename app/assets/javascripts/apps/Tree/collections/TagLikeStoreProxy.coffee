define [ 'underscore', 'backbone' ], (_, Backbone) ->
  attrsWithoutNegativeId = (attrs) ->
    if attrs.id? && attrs.id <= 0
      _.omit(attrs, 'id')
    else
      attrs

  # Proxies a TagStore or SearchResultStore to a Collection.
  #
  # This is a one-way proxy: TagStore changes will propagate to the Tags, but
  # the reverse will not happen.
  #
  # Usage:
  #
  #   proxy = new TagLikeStoreProxy(tagLikeStore)
  #   collection = proxy.collection
  #   tagLikeStore.add(...)
  #   tagLikeStore.remove(...)
  #   tagLikeStore.change(...)
  #   ...
  #   proxy.destroy() # stops listening
  #
  # A Backbone.Collection is more convenient than TagStore, because it fires
  # lots of events with lots of information. It is also slower, because it fires
  # lots of events with lots of information.
  Model = Backbone.Model.extend
    parse: (attrs) -> attrsWithoutNegativeId(attrs)

  Collection = Backbone.Collection.extend
    model: Model

  class TagLikeStoreProxy
    constructor: (tagLikeStore) ->
      @tagLikeStore = tagLikeStore
      @changeOptions = {}

      # Backbone model IDs are different from in TagStore. In Backbone, an
      # unsaved model has no IDs. Our proxy maps from TagStore IDs to models.
      idToModel = @_idToModel = {}
      cidToTag = @_cidToTag = {}
      models = []

      # These methods are unaware of the collection
      createModel = (tagLike) ->
        model = new Model(tagLike, { parse: true })
        idToModel[tagLike.id] = model
        cidToTag[model.cid] = tagLike
        models.push(model)
        model

      changeTagLike = (tagLike) =>
        model = idToModel[tagLike.id]
        model.set(tagLike, @changeOptions)

      changeTagLikeId = (oldId, tagLike) =>
        model = idToModel[oldId]
        delete idToModel[oldId]
        idToModel[tagLike.id] = model
        model.set({ id: tagLike.id }, @changeOptions)

      removeModel = (tagLike) ->
        model = idToModel[tagLike.id]
        delete idToModel[tagLike.id]
        delete cidToTag[model.cid]
        model

      # Now we bring the collection into it
      models = _.map(tagLikeStore.objects, createModel)
      collection = @collection = new Collection(models)

      @callbacks = {
        'added': (tagLike) -> collection.add(createModel(tagLike), { at: tagLike.position })
        'removed': (tagLike) -> collection.remove(removeModel(tagLike))
        'changed': (tagLike) => changeTagLike(tagLike)
        'id-changed': (oldId, tagLike) -> changeTagLikeId(oldId, tagLike)
      }

      @_bind()

    _bind: ->
      for event, callback of @callbacks
        @tagLikeStore.observe(event, callback)

    _unbind: ->
      for event, callback of @callbacks
        @tagLikeStore.unobserve(event, callback)

    setChangeOptions: (@changeOptions) ->

    # Converts from tagLike ID or tagLike object to model
    #
    # The model must already be part of the collection.
    map: (tagLike) ->
      id = tagLike.id? && tagLike.id || tagLike
      throw 'TagLike not found' if id not of @_idToModel
      @_idToModel[id]

    # Converts from model to tagLike
    #
    # The model must already be part of the collection.
    unmap: (model) ->
      @_cidToTag[model.cid]

    destroy: ->
      @_unbind()
