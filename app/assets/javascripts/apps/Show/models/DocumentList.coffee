define [
  'underscore'
  'backbone'
  '../collections/Documents'
  '../models/Document'
  'rsvp'
], (_, Backbone, Documents, Document, RSVP) ->
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
  #
  # Events:
  #
  #   list-tagged(documentList, tag)
  #   list-untagged(documentList, tag)
  class DocumentList extends Backbone.Model
    defaults:
      length: null
      nPagesFetched: 0
      loading: false
      statusCode: null
      error: null

    initialize: (attributes, options) ->
      throw 'Must pass options.params, a DocumentListParams object' if !options.params?
      throw 'Must pass options.url, a String like /documentsets/1234/documents with no question mark' if !options.url?

      @params = options.params
      @url = options.url
      @nDocumentsPerPage = options.nDocumentsPerPage || 20
      @documents = new Documents([])

      @_nextPageTagOps = [] # Array of { op: '(tag|untag)', tag: Tag }

    # Tags all documents, without sending a server request.
    #
    # If a page is being requested, we presume the result might not contain
    # the tag, even though it should. So we add the tag to the next page of
    # results.
    tagLocal: (tag) ->
      for document in @documents.models
        document.tagLocal(tag)

      if @get('loading')
        @_nextPageTagOps.push(op: 'tag', tag: tag)

      @trigger('list-tagged', @, tag)

    # Untags all documents, without sending a server request.
    #
    # If a page is being requested, we presume the result might contain the
    # tag, even though it shouldn't. So we remove the tag from the next page of
    # results.
    untagLocal: (tag) ->
      for document in @documents.models
        document.untagLocal(tag)

      if @get('loading')
        @_nextPageTagOps.push(op: 'untag', tag: tag)

      @trigger('list-untagged', @, tag)

    # Tags all documents, on the server and locally.
    #
    # See `tagLocal()`.
    tag: (tag) ->
      @tagLocal(tag)
      tag.addToDocumentsOnServer(@params.toQueryParams())

    # Untags all documents, on the server and locally.
    #
    # See `untagLocal()`.
    untag: (tag) ->
      @untagLocal(tag)
      tag.removeFromDocumentsOnServer(@params.toQueryParams())

    # Returns true iff we have fetched every document in the list.
    isComplete: ->
      length = @get('length')
      ret = length? && @nDocumentsPerPage * @get('nPagesFetched') >= length
      ret

    # Starts fetching another page of documents.
    #
    # Returns a Promise that resolves when the fetch is complete.
    #
    # Spurious calls are safe: they will return the same Promise.
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
          newDocuments = (new Document(document, parse: true) for document in data.documents)

          if @_nextPageTagOps.length
            for data in @_nextPageTagOps
              # for each new doc: doc.tagLocal(tag) or doc.untagLocal(tag)
              method = "#{data.op}Local"
              tag = data.tag
              document[method](tag) for document in newDocuments
            @_nextPageTagOps = []

          @documents.add(newDocuments)

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
