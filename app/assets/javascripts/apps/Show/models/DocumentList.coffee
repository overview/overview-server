define [
  'backbone'
  '../collections/Documents'
  'rsvp'
], (Backbone, Documents, RSVP) ->
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
  # A DocumentList starts empty. It only ever grows, except the final 'loading'
  # document, which is only ever added to and deleted from the end of the list.
  class DocumentList extends Backbone.Model
    defaults:
      length: null
      nPagesFetched: 0

    initialize: (attributes, options) ->
      throw 'Must pass options.params, a DocumentListParams object' if !options.params?
      throw 'Must pass options.url, a String like /documentsets/1234/documents with no question mark' if !options.url?
      @params = options.params
      @url = options.url
      @nDocumentsPerPage = options.nDocumentsPerPage || 20
      @documents = new Documents([])

      @listenTo(@params.documentSet, 'tag', @_onTag)
      @listenTo(@params.documentSet, 'untag', @_onUntag)

      if @params.searchResult? && !@params.searchResult.get('id')
        # The search result isn't loaded yet, so it can't have any documents.
        @set
          length: 0
          nPagesFetched: 1
        @listenTo(@params.searchResult, 'change:state', @_onSearchResultChangeState)

    _onSearchResultChangeState: (searchResult) ->
      if @params.searchResult == searchResult && searchResult.get('state') == 'Complete'
        @documents.reset([])
        @set
          length: null
          nPagesFetched: 0
          # Setting nPagesFetched to 0 will make DocumentListController check
          # if it needs to start fetching.
      undefined

    _onTag: (tag, params) ->
      if params.equals(@params)
        @documents.tag(tag)
      else if params.document?
        params.document.tag(tag)

    _onUntag: (tag, params) ->
      if params.equals(@params)
        @documents.untag(tag)
      else if params.document?
        params.document.untag(tag)

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
        query = @params.toApiParams()
        query.pageSize = @nDocumentsPerPage
        query.page = @get('nPagesFetched') + 1

        @documents.add([type: 'loading'], parse: true) # dunno why, but no parse:true makes this stop cold in Chrome

        onSuccess = (data) =>
          @documents.pop()
          @documents.add(data.documents, parse: true)
          @set
            length: data.total_items
            nPagesFetched: @get('nPagesFetched') + 1
          resolve(null)

        onError = (err) =>
          console.log('DocumentList fetch error', err)
          setTimeout(fetch, 2000)
          # The promise stays unresolved

        fetch = =>
          Backbone.$.ajax
            type: 'get'
            url: @url
            data: query
            success: onSuccess
            error: onError

        fetch()
