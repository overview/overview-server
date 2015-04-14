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
  #   documentList.list-tagged(documentList, tag)
  #   documentList.list-untagged(documentList, tag)
  #   someTag.documents-changed(tag) # documentList.getTagCount(tag) changed
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

      @listenTo(@documents, 'document-tagged', @_onDocumentTagged)
      @listenTo(@documents, 'document-untagged', @_onDocumentUntagged)

      @_nextPageTagOps = [] # Array of { op: '(tag|untag)', tag: Tag }
      @_tagCounts = {} # Object of Tag CID -> { n: Number, howSure: '(atLeast|exact)' }
      @_halfCountedTags = {} # Object of Tag CID -> Tag

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

      # When the tag is in the DocumentListParams, it's a full count
      paramTags = @params.params.tags
      if paramTags?.length == 1 && tag.id == paramTags[0]
        n = @get('length')
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
            # Tag in-transit documents before adding them to the list. That way,
            # the collection won't send spurious `document-tagged` and
            # `document-untagged` events.
            for op in @_nextPageTagOps
              # for each new doc: doc.tagLocal(tag) or doc.untagLocal(tag)
              method = "#{op.op}Local"
              tag = op.tag
              document[method](tag) for document in newDocuments
            @_nextPageTagOps = []

          @_addDocumentsToTagCounts(newDocuments, data.total_items, @documents.length + newDocuments.length == @get('length'))

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

    _onDocumentTagged: (tag, document, options) ->
      return if options?.fromList
      return unless tag.cid of @_tagCounts
      @_tagCounts[tag.cid].n += 1
      @trigger('tag-counts-changed')

    _onDocumentUntagged: (tag, document, options) ->
      return if options?.fromList
      return unless tag.cid of @_tagCounts
      @_tagCounts[tag.cid].n -= 1
      @trigger('tag-counts-changed')
