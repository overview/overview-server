define [
  'underscore'
  'backbone'
  '../collections/Documents'
  'rsvp'
], (_, Backbone, Documents, RSVP) ->
  # A sorted list of Document objects on the server.
  #
  # A DocumentList is composed of:
  #
  # * the `params` _property_, a `DocumentListParams` object passed as an
  #   option to the constructor.
  # * the `documents` _property_, a `Documents` collection holding the partial,
  #   client-side representation of the server-side list of documents.
  # * the `length` _attribute_, a `Number` representing the number of documents
  #   on the server side. This attribute starts off `null` and changes when the
  #   server responds with some documents.
  #
  # Invoke it like this:
  #
  #   params = DocumentListParams.all()
  #   documentList = new DocumentList({}, params: params)
  #   documentList.fetchNextPage() # returns a Promise
  #
  #   documentList.get('length') # returns null if unknown, Number if known
  #   documentList.on('change:length', ...)
  #
  #   documentList.get('nDocumentsPerPage') # a Number
  #   documentList.get('nFetchedPages') # a Number
  #
  #   docs = documentList.documents
  #   docs.length # useless: only says how many are loaded, plus placeholders
  #   docs.on('add', ...)
  #   docs.on('change', ...) # Happens when tagging
  #   docs.on('remove', ...) # ONLY happens with the "loading" (last) item
  #   docs.on('reset', ...) # NEVER happens
  #
  #   documentList.stopListening() # when you're done with it
  #
  # A DocumentList starts empty. It only ever grows.
  class DocumentList extends Backbone.Model
    defaults:
      length: null
      nPagesFetched: 0
      loading: false
      statusCode: null
      error: null

    initialize: (attributes, options) ->
      throw 'Must pass options.state, a State DELETEME' if !options.state?
      throw 'Must pass options.params, a DocumentListParams object' if !options.params?
      throw 'Must pass options.url, a String like /documentsets/1234/documents with no question mark' if !options.url?
      @params = options.params
      @url = options.url
      @nDocumentsPerPage = options.nDocumentsPerPage || 20
      @documents = new Documents([])

      @listenTo(@params.state, 'tag', @_onTag)
      @listenTo(@params.state, 'untag', @_onUntag)

    _onTag: (tag, params) ->
      if _.isEqual(params, @params.toQueryParams())
        @documents.tag(tag)
      else if params.documents?
        for documentId in (params.documents || '').split(',')
          @documents.get(documentId)?.tag(tag)

    _onUntag: (tag, params) ->
      if _.isEqual(params, @params.toQueryParams())
        @documents.untag(tag)
      else if params.documents?
        for documentId in (params.documents || '').split(',')
          @documents.get(documentId)?.untag(tag)

    isComplete: ->
      length = @get('length')
      ret = length? && @nDocumentsPerPage * @get('nPagesFetched') >= length
      ret

    fetchNextPage: ->
      if @_fetchNextPagePromise?
        @_fetchNextPagePromise
      else if @isComplete()
        RSVP.resolve(null)
      else
        @_fetchNextPagePromise = @_doFetch()
          .then(=> @_fetchNextPagePromise = null) # returns null

    _doFetch: ->
      new RSVP.Promise (resolve, reject) =>
        query = @params.toQueryParams()
        query.limit = @nDocumentsPerPage
        query.offset = @get('nPagesFetched') * @nDocumentsPerPage

        @set(loading: true)

        onSuccess = (data) =>
          @documents.add(data.documents, parse: true)
          @set
            loading: false
            length: data.total_items
            nPagesFetched: @get('nPagesFetched') + 1
          resolve(null)

        onError = (xhr) =>
          message = xhr.responseJSON?.message || xhr.responseText
          @set
            loading: false
            statusCode: xhr.status
            error: message
          reject(message)

        Backbone.$.ajax
          type: 'get'
          url: @url
          data: query
          success: onSuccess
          error: onError

        undefined
