define [
  'underscore'
  'jquery'
  'backbone'
  '../collections/Tags'
  '../collections/SearchResults'
  '../collections/Views'
  './DocumentListParams'
], (_, $, Backbone, Tags, SearchResults, Views, DocumentListParams) ->
  # Holds Tags, SearchResults and Views. Oh, and nDocuments.
  #
  # On the client, a DocumentSet doesn't hold a list of Documents because
  # there are way too many of them. To fetch a sample, use a DocumentList.
  #
  # Because DocumentSet doesn't store Documents, it also doesn't store any
  # associations between its elements: for instance, there is no count of tags
  # per search result. Each set is independent of the others.
  #
  # Methods that change stuff on the server
  # ---------------------------------------
  #
  # These operations assume there is a transaction queue. In particular, you
  # may call tag() and untag() using a Tag that has not yet been saved on the
  # server, even though the POST requests will use the Tag ID. That's because
  # these POST requests won't be created until the previous transactions have
  # all completed.
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

      @nDocuments = null
      @tags = new Tags([], url: "#{@url}/tags")
      @searchResults = new SearchResults([], url: "#{@url}/searches")
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
      @nDocuments = data.nDocuments
      @tags.reset(data.tags)
      @searchResults.reset(data.searchResults)
      @views.reset(data.views)

    _onError: (xhr) ->
      console.log('ERROR loading document set', xhr)

    tag: (tag, documentListParams) ->
      @transactionQueue.ajax =>
        type: 'POST'
        url: "/documentsets/#{@id}/tags/#{tag.id}/add"
        data: documentListParams.toApiParams()
        debugInfo: 'DocumentSet.tag'

      @trigger('tag', tag, documentListParams)

    untag: (tag, documentListParams) ->
      @transactionQueue.ajax =>
        type: 'POST'
        url: "/documentsets/#{@id}/tags/#{tag.id}/remove"
        data: documentListParams.toApiParams()
        debugInfo: 'DocumentSet.untag'

      @trigger('untag', tag, documentListParams)
