define [ 'underscore' ], (_) ->
  # Describes a document list
  #
  # For instance:
  #
  #     params = DocumentListParams.all()
  #     params.type       # "all" -- useful for crafting user-visible messages
  #     params.params     # [] -- useful for creafting user-visible messages
  #     params.toString() # "DocumentListParams(root)"
  #     params.findDocumentsFromCache(cache) # Array: all documents in cache
  #     params.findTagCountsFromCache(cache) # Object: all tag id -> count for the entire docset, according to TagStore
  #
  #     params2 = DocumentListParams.byNodeId(2)
  #     params2.type       # "node"
  #     params2.params     # [ 2 ]
  #     params2.toString() # "DocumentListParams(node=2)"
  #     params2.findDocumentsFromCache(cache) # Array: all docs in cache in node 2
  #     params2.findTagCountsFromCache(cache) # null: we cannot know how many of each tag are finded in the entire docset
  #
  #     params.equals(params2) # false -- unless node 2 is the root node
  #
  #     params.toApiParams() # Params for the public client/server API
  #
  # Here are all the possibilities:
  #
  #     DocumentListParams.all() # all
  #     DocumentListParams.untagged() # all with zero tags
  #     DocumentListParams.byNodeId(Number) # by Node
  #     DocumentListParams.byTagId(Number) # by Tag
  #     DocumentListParams.bySearchResult(Number) # by Search Result
  #
  # Each object is immutable.
  class AbstractDocumentListParams
    constructor: (@type, @params...) ->

    toString: ->
      if @params.length
        "DocumentListParams(#{@type}:#{@params.join(',')})"
      else
        "DocumentListParams(#{@type})"

    # Returns true iff the types of this object and rhs were constructed using
    # the same parameters.
    equals: (rhs) ->
      @type == rhs.type && _.isEqual(@params, rhs.params)

    # Returns an Array of Document objects.
    #
    # We use this to optimize some obvious cases: for instance, when we send
    # a tag operation to the server, we can predict which of the
    # locally-shown documents will change and update them ahead of time.
    #
    # The default result, `[]`, must work in all cases.
    findDocumentsFromCache: (cache) -> []

    # Returns an Object of tagId -> count, or `null` if we cannot be sure
    #
    # We use this to optimize some obvious cases: for instance, if we tag the
    # root node, we can update counts in the TagStore.
    #
    # The default result, `null`, must work in all cases.
    findTagCountsFromCache: (cache) -> null

    # Returns the parameters in pure JSON format.
    #
    # For instance, a hypothetical server API might accept a `POST` to
    # ".../tag?nodes[]=2". This method would return `{ nodes: 2 }` to help
    # generate that URL.
    toJSON: -> throw new Error('not implemented')

    # Returns the parameters such that Overview servers can understand them.
    #
    # For instance, if we want ".../tag/34/add?nodes=2", then this method
    # should return `{ nodes: '2' }`.
    #
    # Overview's servers expect each ID array to be a single String, with
    # commas delimiting each ID.
    #
    # You must pass baseParams, a filter that describes the app the user is
    # looking at. For instance, the Tree app is always rooted at a node; set a
    # filter of `{ nodes: rootNodeId }` and that will be added to the API iff
    # there is no node in the selection already. (If there _is_ a node in the
    # selection, then presumably it is a child of the one you're passing in the
    # filter, meaning it's okay to omit it.)
    toApiParams: (filter) ->
      ret = {}
      for k, v of @toJSON()
        ret[k] = v.map(String).join(',')
      for k, v of (filter ? {})
        if k not of ret
          ret[k] = v
      ret

  MagicUntaggedTagId = 0

  sortDocumentsArray = (documentsArray) ->
    documentsArray.sort (a, b) ->
      (a.title || '').toLowerCase().localeCompare((b.title || '').toLowerCase()) ||
        (a.description || '').toLowerCase().localeCompare((b.description || '').toLowerCase()) ||
        a.id - b.id
    documentsArray

  class AllDocumentListParams extends AbstractDocumentListParams
    constructor: -> super('all')

    findDocumentsFromCache: (cache) ->
      ret = (document for __, document of cache.document_store.documents)
      sortDocumentsArray(ret)

    toJSON: -> {}

  class DocumentDocumentListParams extends AbstractDocumentListParams
    constructor: (@documentId) -> super('document', @documentId)

    findDocumentsFromCache: (cache) ->
      d = cache.document_store.documents[@documentId]
      d? && [d] || []

    toJSON: -> { documents: [ @documentId ] }

  class NodeDocumentListParams extends AbstractDocumentListParams
    constructor: (@nodeId) -> super('node', @nodeId)

    findDocumentsFromCache: (cache) ->
      ret = (d for __, d of cache.document_store.documents when @nodeId in d.nodeids)
      sortDocumentsArray(ret)

    toJSON: -> { nodes: [ @nodeId ] }

  class TagDocumentListParams extends AbstractDocumentListParams
    constructor: (@tagId) -> super('tag', @tagId)

    findDocumentsFromCache: (cache) ->
      ret = (d for __, d of cache.document_store.documents when @tagId in d.tagids)
      sortDocumentsArray(ret)

    toJSON: -> { tags: [ @tagId ] }

  class UntaggedDocumentListParams extends AbstractDocumentListParams
    constructor: -> super('untagged')

    findDocumentsFromCache: (cache) ->
      ret = (d for __, d of cache.document_store.documents when d.tagids.length == 0)
      sortDocumentsArray(ret)

    toJSON: -> { tags: [ MagicUntaggedTagId ] }

  class IntersectionDocumentListParams extends AbstractDocumentListParams
    constructor: (@list1, @list2) ->
      throw 'Intersection only works when components are of different types' if @list1.type == @list2.type

      super('intersection', [@list1.type, @list1.params], [@list2.type, @list2.params])

    toString: -> "#{@list1.toString()} ∩ #{@list2.toString()}"

    toJSON: -> _.extend({}, @list1.toJSON(), @list2.toJSON())

    findDocumentsFromCache: (cache) ->
      # Not terribly efficient...
      ret1 = @list1.findDocumentsFromCache(cache)
      ret2 = @list2.findDocumentsFromCache(cache)
      ret = _.intersection(ret1, ret2)
      sortDocumentsArray(ret)

  class SearchResultDocumentListParams extends AbstractDocumentListParams
    constructor: (@searchResultId) -> super('searchResult', @searchResultId)

    toJSON: -> { searchResults: [ @searchResultId ] }

  {
    all: -> new AllDocumentListParams()
    intersect: (a, b) -> new IntersectionDocumentListParams(a, b)
    byDocumentId: (documentId) -> new DocumentDocumentListParams(documentId)
    byNodeId: (nodeId) -> new NodeDocumentListParams(nodeId)
    byTagId: (tagId) -> new TagDocumentListParams(tagId)
    untagged: (tagId) -> new UntaggedDocumentListParams()
    bySearchResultId: (searchResultId) -> new SearchResultDocumentListParams(searchResultId)
  }
