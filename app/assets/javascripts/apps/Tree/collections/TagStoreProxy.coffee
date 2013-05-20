define [ 'underscore', 'backbone' ], (_, Backbone) ->
  attrsWithoutNegativeId = (attrs) ->
    if attrs.id? && attrs.id <= 0
      _.omit(attrs, 'id')
    else
      attrs

  # Proxies a TagStore to a Collection.
  #
  # This is a one-way proxy: TagStore values will propagate to the Tags, but
  # the reverse will not happen.
  #
  # Usage:
  #
  #   proxy = new TagStoreProxy(tagStore)
  #   collection = proxy.collection
  #   tagStore.create_tag(...)
  #   tagStore.add(...)
  #   tagStore.remove(...)
  #   tagStore.change(...)
  #   ...
  #   proxy.destroy() # stops listening
  #
  # A Backbone.Collection is more convenient than TagStore, because it fires
  # lots of events. It is also slower, because it fires lots of events.
  Model = Backbone.Model.extend
    parse: (attrs) -> attrsWithoutNegativeId(attrs)

  Collection = Backbone.Collection.extend
    model: Model

  class TagStoreProxy
    constructor: (tagStore) ->
      @tagStore = tagStore
      @changeOptions = {}

      # Backbone model IDs are different from in TagStore. In Backbone, an
      # unsaved model has no IDs. Our proxy maps from TagStore IDs to models.
      idToModel = @_idToModel = {}
      cidToTag = @_cidToTag = {}
      models = []

      # These methods are unaware of the collection
      createModel = (tag) ->
        model = new Model(tag, { parse: true })
        idToModel[tag.id] = model
        cidToTag[model.cid] = tag
        models.push(model)
        model

      changeTag = (tag) =>
        model = idToModel[tag.id]
        model.set(tag, @changeOptions)

      changeTagId = (oldTagId, tag) =>
        model = idToModel[oldTagId]
        delete idToModel[oldTagId]
        idToModel[tag.id] = model
        model.set({ id: tag.id }, @changeOptions)

      removeModel = (tag) ->
        model = idToModel[tag.id]
        delete idToModel[tag.id]
        delete cidToTag[model.cid]
        model

      # Now we bring the collection into it
      models = _.map(tagStore.tags, createModel)
      collection = @collection = new Collection(models)

      @callbacks = {
        'tag-added': (tag) -> collection.add(createModel(tag), { at: tag.position })
        'tag-removed': (tag) -> collection.remove(removeModel(tag))
        'tag-changed': (tag) => changeTag(tag)
        'tag-id-changed': (oldTagId, tag) -> changeTagId(oldTagId, tag)
      }

      @_bind()

    _bind: ->
      for event, callback of @callbacks
        @tagStore.observe(event, callback)

    _unbind: ->
      for event, callback of @callbacks
        @tagStore.unobserve(event, callback)

    setChangeOptions: (@changeOptions) ->

    # Converts from tag ID or tag object to model
    #
    # The model must already be part of the collection.
    map: (tag) ->
      id = tag.id? && tag.id || tag
      throw 'Tag not found' if id not of @_idToModel
      @_idToModel[id]

    # Converts from model to tag
    #
    # The model must already be part of the collection.
    unmap: (model) ->
      @_cidToTag[model.cid]

    destroy: ->
      @_unbind()
