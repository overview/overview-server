define [
  'underscore'
  'backbone'
  '../collections/Documents'
  '../models/Document'
], (_, Backbone, Documents, Document) ->
  # A sorted list of Document objects on the server.
  #
  # A DocumentList is composed of:
  #
  # * the `documentSet` _property_, a `DocumentSet` object passed as an option
  #   to the constructor.
  # * the `params` _property_, a `DocumentListParams` object passed as an
  #   option to the constructor.
  # * the `documents` _property_, a `Documents` collection holding the partial,
  #   client-side representation of the server-side list of documents.
  # * the `length` _attribute_, a `Number` representing the number of documents
  #   on the server side. This attribute starts off `null` and changes when the
  #   server responds with some documents.
  # * the `progress` _attribute_, a `Number` from 0.0 to 1.0 representing how
  #   far along the server is in preparing results. It only makes sense when
  #   fetching the first page.
  # * the `selectionId` _attribute_, null to begin with and then a UUID.
  #
  # Invoke it like this:
  #
  #   documentSet = new DocumentSet(...)
  #   params = new DocumentListParams()
  #   documentList = new DocumentList({}, documentSet: documentSet, params: params)
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
  # You can pass a "reverse" parameter to add "&reverse=true" to requests. The
  # server will return the last document first in that case. (Pass "reverse"
  # alongside "params" and "documentSet" as a _parameter_, not an _attribute_.)
  #
  # Events:
  #
  #   documentList.list-tagged(documentList, tag)
  #   documentList.list-untagged(documentList, tag)
  #   someTag.documents-changed(tag) # documentList.getTagCount(tag) changed
  class DocumentList extends Backbone.Model
    defaults:
      error: null
      warnings: []
      length: null
      progress: null
      loading: false
      nPagesFetched: 0
      selectionId: null
      statusCode: null

    initialize: (attributes, options) ->
      throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet?
      throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue?
      throw 'Must pass options.params, a DocumentListParams object' if !options.params?

      @documentSet = options.documentSet
      @transactionQueue = options.transactionQueue
      @params = options.params
      @reverse = !!options.reverse
      @url = _.result(@documentSet, 'url').replace(/\.json$/, '') + '/documents'
      @nDocumentsPerPage = options.nDocumentsPerPage || 20
      @documents = new Documents([])
      @documents.url = @url

      @listenTo(@documents, 'document-tagged', @_onDocumentTagged)
      @listenTo(@documents, 'document-untagged', @_onDocumentUntagged)

      @_nextPageTagOps = [] # Array of { op: '(tag|untag)', tag: Tag }
      @_tagCounts = {} # Object of Tag CID -> { n: Number, howSure: '(atLeast|exact)' }
      @_halfCountedTags = {} # Object of Tag CID -> Tag

    # Returns the query string when we want to refresh the query on the server.
    #
    # Normally, the server will cache any query results for a given query
    # string. If we add, remove or tag documents, the cached results won't
    # change. This method adds a ?refresh=true to the query string to ensure
    # the server ignores its cache.
    _getQueryStringNoCache: ->
      ret = @params.toQueryString()
      ret && "#{ret}&refresh=true" || 'refresh=true'

    # Returns the query string when we want to use the server's cached values.
    #
    # When we fetch the first page of results, the server will return a
    # selection ID, which we can use to paginate results.
    #
    # If we haven't fetched the first page of results yet, this method will
    # return `null`.
    _getQueryStringCached: ->
      if @get('selectionId')
        q = if @params.q then "q=#{@params.q}&" else ""
        q = "#{q}selectionId=#{@get('selectionId')}"
        # XXX ugly bug: the server doesn't cache sortByMetadataField, but it
        # uses it when outputting results. Tell it which field we're sorting by,
        # so it will include the field in its output.
        #
        # This isn't a proper solution, and it isn't tested. But it's really
        # easy to implement, and [adam, 2017-06-30] I'm not sure what a proper
        # solution would look like yet.
        if @params.sortByMetadataField
          q = "#{q}&sortByMetadataField=#{encodeURIComponent(@params.sortByMetadataField)}"
        q
      else
        null

    # Tags all documents, without sending a server request.
    #
    # If a page is being requested, we presume the result might not contain
    # the tag, even though it should. So we add the tag to the next page of
    # results.
    tagLocal: (tag) ->
      # If the list hasn't loaded, that will make n=null. That's okay.
      #
      # XXX There's an icky race: 1. tag the list before it's loaded; 2. untag
      # an item (n=-1). We assume this won't happen in the real world. The
      # impact is minor: just set new DocumentListParams and all will be well.
      delete @_halfCountedTags[tag.cid]
      @_tagCounts[tag.cid] = { n: @get('length'), howSure: 'exact' }
      for document in @documents.models
        document.tagLocal(tag, fromList: true)

      if @get('loading')
        @_nextPageTagOps.push(op: 'tag', tag: tag)

      @trigger('list-tagged', @, tag)
      @trigger('tag-counts-changed')

    # Untags all documents, without sending a server request.
    #
    # If a page is being requested, we presume the result might contain the
    # tag, even though it shouldn't. So we remove the tag from the next page of
    # results.
    untagLocal: (tag) ->
      delete @_halfCountedTags[tag.cid]
      @_tagCounts[tag.cid] = { n: 0, howSure: 'exact' }
      for document in @documents.models
        document.untagLocal(tag, fromList: true)

      if @get('loading')
        @_nextPageTagOps.push(op: 'untag', tag: tag)

      @trigger('list-untagged', @, tag)
      @trigger('tag-counts-changed')

    # Tags all documents, on the server and locally.
    #
    # If no documents have loaded yet, this method will wait until the first
    # page load. That's so we can use `getSelectionQueryParams()`.
    #
    # See `tagLocal()`.
    tag: (tag) ->
      if @get('selectionId')?
        @tagLocal(tag)
        tag.addToDocumentsOnServer(selectionId: @get('selectionId'))
      else
        @fetchNextPage().then(=> @tag(tag))

    # Untags all documents, on the server and locally.
    #
    # If no documents have loaded yet, this method will wait until the first
    # page load. That's so we can use `getSelectionQueryParams()`.
    #
    # See `untagLocal()`.
    untag: (tag) ->
      if @get('selectionId')?
        @untagLocal(tag)
        tag.removeFromDocumentsOnServer(selectionId: @get('selectionId'))
      else
        @fetchNextPage().then(=> @untag(tag))

    # Returns true iff we have fetched every document in the list.
    isComplete: ->
      # The server may suddenly clear a document list, which would cause
      # subsequent pages to be empty. That would make
      # @get('length') > @documents.length, always.
      length = @get('length')
      length? && @nDocumentsPerPage * @get('nPagesFetched') >= length

    # Returns the number of documents with the given tag.
    #
    # There are several possible results:
    #
    # * { n: null, howSure: 'exact' }: all documents have this tag, and we do
    #   not know the length of the list.
    # * { n: n, howSure: 'exact' }: n documents have this tag.
    # * { n: 0, howSure: 'exact' }: 0 documents have this tag.
    # * { n: n, howSure: 'atLeast' }: n or more documents have this tag.
    # * { n: 0, howSure: 'atLeast' }: we have no idea whether this tag is set.
    #
    # We use the following logic to track this lazily-computed, cached,
    # live result:
    #
    # * getTagCount() lazily sets n=(count what we have), howSure=atLeast
    # * getTagCount() lazily sets n=length, howSure=exact on tag params
    # * each load, n+=(count the new ones) for each tag with howSure=atLeast
    # * tagLocal() sets n=length, howSure=exact
    # * untagLocal() sets n=0, howSure=exact
    # * documents.document-tagged() sets n+=1
    # * documents.document-untagged() sets n-=1
    #
    # Notice that we can only transition from atLeast->exact.
    #
    # Listen for tag-counts-changed() to be notified when the result might
    # change. It has no arguments: it applies to all tags.
    getTagCount: (tag) ->
      return @_tagCounts[tag.cid] if tag.cid of @_tagCounts

      # When the DocumentListParams is just the tag, it's a full count
      if _.isEqual(@params.toJSON(), tags: [ tag.id ])
        n = @get('length')
        howSure = 'exact'
      else if _.isEqual(@params.toJSON(), tags: [ tag.id ], tagOperation: 'none')
        n = 0
        howSure = 'exact'
      else
        n = 0
        (n += 1) for document in @documents.models when document.hasTag(tag)
        howSure = @isComplete() && 'exact' || 'atLeast'

      if howSure == 'atLeast'
        # We'll need to call document.hasTag(tag) on subsequent tags. See
        # _addDocumentsToTagCounts().
        @_halfCountedTags[tag.cid] = tag

      @_tagCounts[tag.cid] =
        n: n
        howSure: howSure

    # Updates @_tagCounts and sometimes clears @_halfCountedTags.
    #
    # See getTagCount() for how this works.
    #
    # Params:
    # * newDocuments: documents that are not yet part of @documents
    # * totalLength: soon to be @get('length'), if @get('length') == null
    # * isLastPage: true iff we're loading the final page
    _addDocumentsToTagCounts: (newDocuments, totalLength, isLastPage) ->
      for tagCid, count of @_tagCounts
        if count.howSure == 'atLeast'
          tag = @_halfCountedTags[tagCid]
          (count.n += 1) for document in newDocuments when document.hasTag(tag)
          count.howSure = 'exact' if isLastPage
        else # howSure == 'exact'
          if !count.n?
            # This is the first page, and we tagged the list before we got
            # here.
            count.n = totalLength
          # Otherwise, if n=? and howSure=exact, we already know whether
          # these new documents have been tagged; n won't change.
      @_halfCountedTags = {} if isLastPage

      @trigger('tag-counts-changed')

    # Starts fetching another page of documents.
    #
    # Returns a Promise that resolves when the fetch is complete.
    #
    # Spurious calls are safe: they will return the same Promise.
    fetchNextPage: ->
      if @_fetchNextPagePromise?
        @_fetchNextPagePromise
      else if @isComplete()
        Promise.resolve(null)
      else
        @_fetchNextPagePromise = @_doFetch()
          .then(=> @_fetchNextPagePromise = null) # returns null

    _receiveProgress: (data) ->
      if @get('length') == null
        @set(progress: data.progress)

    _receivePage: (data) ->
      newDocuments = (new Document(document, parse: true) for document in data.documents)

      if @_nextPageTagOps.length
        # Tag in-transit documents before adding them to the list. That way,
        # the collection won't send spurious `document-tagged` and
        # `document-untagged` events.
        for op in @_nextPageTagOps
          # for each new doc: doc.tagLocal(tag) or doc.untagLocal(tag)
          method = "#{op.op}Local"
          tag = op.tag
          document[method](tag) for document in newDocuments
        @_nextPageTagOps = []

      @_addDocumentsToTagCounts(
        newDocuments,
        data.total_items,
        @documents.length + newDocuments.length == @get('length')
      )

      @documents.add(newDocuments)

      @set
        loading: false
        progress: null
        length: data.total_items
        selectionId: data.selection_id
        warnings: data.warnings
        nPagesFetched: @get('nPagesFetched') + 1

    _receiveError: (errorReport) ->
      @set
        loading: false
        progress: null
        statusCode: errorReport.statusCode
        error: errorReport.jsonBody.message

    _doFetch: ->
      query = if @get('length') == null
        @_getQueryStringNoCache()
      else
        @_getQueryStringCached()

      query += "&limit=#{@nDocumentsPerPage}"
      query += "&offset=#{@get('nPagesFetched') * @nDocumentsPerPage}"
      query += "&reverse=true" if @reverse

      @set(loading: true)

      @transactionQueue.streamJsonArray({
        url: "#{@url}?#{query}"
        onItem: (item) =>
          if item.progress
            @_receiveProgress(item)
          else
            @_receivePage(item)
        onStartError: (errorReport) =>
          @_receiveError(errorReport)
      })

    _onDocumentTagged: (document, tag, options) ->
      return if options?.fromList
      return unless tag.cid of @_tagCounts
      @_tagCounts[tag.cid].n += 1
      @trigger('tag-counts-changed')

    _onDocumentUntagged: (document, tag, options) ->
      return if options?.fromList
      return unless tag.cid of @_tagCounts
      @_tagCounts[tag.cid].n -= 1
      @trigger('tag-counts-changed')
