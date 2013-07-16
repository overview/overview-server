define [ 'jquery' ], ($) ->
  makeUrlFunctions = (baseUrl) ->
    queryString = window.csrfTokenQueryString && "?#{window.csrfTokenQueryString}" || ''

    create: (tagLike) -> "#{baseUrl}#{queryString}"
    update: (tagLike) -> "#{baseUrl}/#{tagLike.id}#{queryString}"
    destroy: (tagLike) -> "#{baseUrl}/#{tagLike.id}#{queryString}"

  # A TagLikeApi sends updates to the server via REST HTTP calls. It is mostly
  # _independent_ of TagLikeStore.
  #
  # All server calls are piped through the given TransactionQueue and return
  # a jQuery.Deferred which will be resolved when the request has completed.
  #
  # Methods:
  #
  # * create(tagLike): creates a tagLike; eventually sets tagLike.id. (POST /)
  # * update(tagLike): sets new attributes on the tagLike. (PUT /:id)
  # * destroy(tagLike): deletes the tagLike. (DELETE /:id)
  #
  # This is not a TagLikeStore! You should use methods like this:
  #
  # * create: tagLike = store.add(attributes); api.create(tagLike)
  # * update: tagLike = store.change(tagLike); api.update(tagLike)
  # * destroy: store.remove(tagLike); api.destroy(tagLike)
  #
  # A TagLike must have these properties (plus optional ones):
  #
  # * id: negative before the object is created; positive after.
  #
  # The TagLikeStore deals with local data only, which includes data that is
  # not yet on the server. The TagLikeApi is unaware of local data (and server
  # data, for that matter).
  #
  # Concerning IDs of new objects: this code is just fine:
  #
  #     tagLike = store.add(attributes) # assigns a negative tagLike.id
  #     api.create(tagLike) # will eventually make tagLike.id positive
  #     api.update(tagLike) # at this point, tagLike.id is still negative
  #
  # The TransactionQueue will postpone update() until after the create() is
  # finished, and tagLike will be modified in-place. So when the update() logic
  # runs, tagLike.id is positive and we're happy.
  #
  # Parameters:
  #
  # * store: a TagLikeStore, used for setting the new ID.
  # * transactionQueue: a TransactionQueue
  # * urlRoot: base URL (methods will use sub-urls).
  #
  # Each operation accepts an options field. The following options are
  # supported:
  #
  # * beforeReceive: method to call immediately after the server returns,
  #   before any other callbacks
  # * (there is no afterReceive: use .done() instead)
  class TagLikeData
    constructor: (@store, @transactionQueue, urlRoot) ->
      @_urlBuilders = makeUrlFunctions(urlRoot)

    _queue: (options) ->
      @transactionQueue.queue =>
        options.ajaxOptions.url = options.url()
        ret = $.ajax(options.ajaxOptions)
        ret.done(options.beforeReceive) if options.beforeReceive?
        ret.done(options.callback) if options.callback?
        ret

    create: (tagLike, options) ->
      options = _.extend({}, options, {
        url: => @_urlBuilders.create(tagLike)
        ajaxOptions:
          type: 'POST'
          data: tagLike
        callback: (tagLikeFromServer) =>
          @store.change(tagLike, tagLikeFromServer)
      })

      @_queue(options)

    update: (tagLike, options) ->
      options = _.extend({}, options, {
        url: => @_urlBuilders.update(tagLike)
        ajaxOptions:
          type: 'PUT'
          data: tagLike
      })

      @_queue(options)

    destroy: (tagLike, options) ->
      options = _.extend({}, options, {
        url: => @_urlBuilders.destroy(tagLike)
        ajaxOptions:
          type: 'DELETE'
      })

      @_queue(options)
