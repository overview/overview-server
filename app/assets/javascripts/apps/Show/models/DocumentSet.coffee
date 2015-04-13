define [
  'underscore'
  'jquery'
  'backbone'
  '../collections/Tags'
  '../collections/Views'
  './DocumentListParams'
], (_, $, Backbone, Tags, Views, DocumentListParams) ->
  # Calls callback() as soon as model.isNew() is false
  #
  # Sometimes this is synchronous
  whenExists = (model, callback, listener) ->
    if !model.isNew()
      callback()
    else
      listener.listenToOnce(model, 'sync', -> whenExists(model, callback, listener))

  # Holds Tags and Views.
  #
  # On the client, a DocumentSet doesn't hold a list of Documents because
  # there are way too many of them. To fetch a sample, use a DocumentList.
  #
  # Because DocumentSet doesn't store Documents, it also doesn't store any
  # associations between its elements: for instance, there is no count of tags
  # per node. Each set is independent of the others.
  #
  # Methods that change stuff on the server
  # ---------------------------------------
  #
  # You may call tag() and untag() using a Tag. If the tag hasn't been saved to
  # the server yet, the actual tagging operation will be postponed until it
  # has.
  #
  # tag: (tag, documentListParams): tells the server to tag a set of documents.
  # untag: (tag, documentListParams): tells the server to untag documents.
  #
  # Events
  # ------
  #
  # DocumentSet extends Backbone.Events. It emits these:
  #
  # * tag: (tag, documentListParams): emitted after a tag is POSTed to the
  #   server, but (probably) before the server acknowledges.
  # * untag: (tag, documentListParams): emitted after an untag is POSTed to
  #   the server, but (probably) before the server acknowledges.
  class DocumentSet
    constructor: (@id, @transactionQueue) ->
      _.extend(@, Backbone.Events)

      @url = "/documentsets/#{@id}"

      @tags = new Tags([], url: "#{@url}/tags")
      @views = new Views([], url: "#{@url}/views")

      @_load()

    documentListParams: (view) ->
      new DocumentListParams(@, view)

    _load: ->
      Backbone.ajax
        type: 'get'
        url: "#{@url}.json"
        success: (data) => @_onSuccess(data)
        error: (xhr) => @_onError(xhr)

    _onSuccess: (data) ->
      @tags.reset(data.tags)
      @views.reset(data.views)

    _onError: (xhr) ->
      console.log('ERROR loading document set', xhr)

    tag: (tag, queryParams) ->
      call = =>
        @transactionQueue.ajax
          type: 'POST'
          url: "/documentsets/#{@id}/tags/#{tag.id}/add"
          data: queryParams
          debugInfo: 'DocumentSet.tag'

      whenExists(tag, call, @)

      @trigger('tag', tag, queryParams)

    untag: (tag, queryParams) ->
      call = =>
        @transactionQueue.ajax
          type: 'POST'
          url: "/documentsets/#{@id}/tags/#{tag.id}/remove"
          data: queryParams
          debugInfo: 'DocumentSet.untag'

      whenExists(tag, call, @)

      @trigger('untag', tag, queryParams)
