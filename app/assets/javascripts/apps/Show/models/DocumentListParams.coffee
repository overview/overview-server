define [ 'underscore' ], (_) ->
  # Describes a document list
  #
  # For instance:
  #
  #     params = DocumentListParams(documentSet).all()
  #     params.type       # "all" -- useful for crafting user-visible messages
  #     params.params     # [] -- useful for creafting user-visible messages
  #     params.toString() # "DocumentListParams(root)"
  #     params.findDocumentsInList(documentList) # Array: all documents
  #
  #     params2 = params.reset.byNode(node)
  #     params2.type       # "node"
  #     params2.params     # [ 2 ] -- the node ID
  #     params2.toString() # "DocumentListParams(node=2)"
  #     params2.findDocumentsInList(documentList) # Array: all docs in cache with node 2
  #
  #     params.equals(params2) # false -- unless node 2 is the root node
  #
  #     params.toApiParams() # Params for the public client/server API
  #
  # Here are all the possibilities:
  #
  #     DocumentListParams(documentSet).all()
  #     DocumentListParams(documentSet).untagged()
  #     DocumentListParams(documentSet).byNode(node)
  #     DocumentListParams(documentSet).byTag(tag)
  #     DocumentListParams(documentSet).bySearchResult(searchResult)
  #
  # Each object is immutable.
  class AbstractDocumentListParams
    constructor: (@documentSet, @viz, @type, @params...) ->
      @reset = new DocumentListParamsBuilder(@documentSet, @viz)

    toString: ->
      if @params.length
        ids = (x.id for x in @params)
        "DocumentListParams(#{@type}:#{ids.join(',')})"
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
    findDocumentsInList: (documentList) -> []

    # Returns the parameters in pure JSON format.
    #
    # For instance, a hypothetical server API might accept a `POST` to
    # ".../tag?nodes[]=2". This method would return `{ nodes: 2 }` to help
    # generate that URL.
    toJSON: -> throw new Error('not implemented')

    # Returns all you need for i18n-ized names.
    #
    # For instance:
    #
    # * [ 'all' ]
    # * [ 'node', 'node description' ]
    # * [ 'tag', 'tag name' ]
    # * [ 'untagged' ]
    # * [ 'searchResult', 'query' ]
    toI18n: -> throw new Error('not implemented')

    # Returns the parameters such that Overview servers can understand them.
    #
    # For instance, if we want ".../tag/34/add?nodes=2", then this method
    # should return `{ nodes: '2' }`.
    #
    # Overview's servers expect each ID array to be a single String, with
    # commas delimiting each ID.
    #
    # If there is a Viz, this selection's return value will be passed through
    # Viz.scopeApiParams(). For instance, a Tree viz can add a `node` property
    # to selections that don't already have one.
    toApiParams: ->
      apiParams = {}
      for k, v of @toJSON() when k != 'name'
        apiParams[k] = _.flatten([v]).map(String).join(',')

      if @viz?
        @viz.scopeApiParams(apiParams)
      else
        apiParams

  MagicUntaggedTagId = 0

  sortDocumentsArray = (documentsArray) ->
    documentsArray.sort (a, b) ->
      (a.title || '').toLowerCase().localeCompare((b.title || '').toLowerCase()) ||
        (a.description || '').toLowerCase().localeCompare((b.description || '').toLowerCase()) ||
        a.id - b.id
    documentsArray

  class AllDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz) -> super(documentSet, viz, 'all')

    findDocumentsInList: (list) -> list

    toJSON: -> {}

    toI18n: -> [ 'all' ]

  class DocumentDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz, @document) -> super(documentSet, viz, 'document', @document)

    findDocumentsInList: (list) ->
      documentId = @document.id
      list.filter((x) -> documentId == x.id)

    toJSON: -> { documents: [ @document.id ] }

  class NodeDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz, @node) -> super(documentSet, viz, 'node', @node)

    findDocumentsInList: (list) ->
      nodeId = @node.id
      list.filter((d) -> nodeId in d.attributes.nodeids)

    toJSON: -> { nodes: [ @node.id ] }

    toI18n: -> [ 'node', @node.description || '' ]

  class TagDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz, @tag) -> super(documentSet, viz, 'tag', @tag)

    findDocumentsInList: (list) ->
      tagId = @tag.id
      list.filter((d) -> tagId in d.attributes.tagids)

    toJSON: -> { tags: [ @tag.id ] }

    toI18n: -> [ 'tag', @tag.attributes?.name || '' ]

  class UntaggedDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz) -> super(documentSet, viz, 'untagged')

    findDocumentsInList: (list) -> list.filter((x) -> x.attributes.tagids.length == 0)

    toJSON: -> { tags: [ MagicUntaggedTagId ] }

    toI18n: -> [ 'untagged' ]

  class SearchResultDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz, @searchResult) -> super(documentSet, viz, 'searchResult', @searchResult)

    toJSON: -> { searchResults: [ @searchResult.get('id') || 0 ] }

    toI18n: -> [ 'searchResult', @searchResult.attributes.query || '' ]

  class JsonDocumentListParams extends AbstractDocumentListParams
    constructor: (documentSet, viz, @json) -> super(documentSet, viz, 'json', @json)

    toString: -> "DocumentListParams(json, #{JSON.stringify(@json)})"

    toJSON: -> @json

    toI18n: -> [ 'json', @json.name ]

  class DocumentListParamsBuilder
    constructor: (@documentSet, @viz) ->
    withViz: (viz) -> new DocumentListParamsBuilder(@documentSet, viz)

    all: -> new AllDocumentListParams(@documentSet, @viz)
    byDocument: (document) -> new DocumentDocumentListParams(@documentSet, @viz, document)
    byJson: (json) -> new JsonDocumentListParams(@documentSet, @viz, json)
    byNode: (node) -> new NodeDocumentListParams(@documentSet, @viz, node)
    byTag: (tag) -> new TagDocumentListParams(@documentSet, @viz, tag)
    bySearchResult: (searchResult) -> new SearchResultDocumentListParams(@documentSet, @viz, searchResult)
    untagged: -> new UntaggedDocumentListParams(@documentSet, @viz)
