define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.DocumentListTitle.#{key}", args...)

  templates =
    # Loading...
    loading: _.template("""
      <div class="loading">
        <h4><%= t('loading') %></h4>
      </div>
    """)

    # <strong>4 documents</strong> in tag "Tag"
    # Params: t, nDocuments, tag
    tag: _.template("""
      <div class="tag" data-id="<%- tag.id %>">
        <a class="tag-edit"><%- t('tag.edit') %></a>
        <h4><%= t('tag.title_html', t('num_documents', nDocuments), tag.name) %></h4>
      </div>
    """)

    # <strong>4 documents</strong> in node "Node"
    # Params: t, nDocuments, node
    node: _.template("""
      <div class="node" data-id="<%- node.id %>">
        <a class="node-edit"><%- t('node.edit') %></a>
        <h4><%= t('node.title_html', t('num_documents', nDocuments), node.description) %></h4>
      </div>
    """)

    # <strong>4 documents</strong> in search "Search"
    # Params: t, nDocuments, searchResult
    searchResult: _.template("""
      <div class="search-result" data-id="<%- searchResult.id %>">
        <h4><%= t('searchResult.title_html', t('num_documents', nDocuments), searchResult.query) %></h4>
      </div>
    """)

    # <strong>4 documents</strong> in selection
    # Params: t, nDocuments, nTags, nNodes
    multiple: _.template("""
      <div class="tags-nodes">
        <h4><%= t('multiple.title_html', t('num_documents', nDocuments)) %></h4>
      </div>
    """)

  # Shows what's currently selected
  #
  # Usage:
  #
  #   cache = new Cache(...)
  #   documentList = new DocumentList(...)
  #   view = new SelectionTitle({
  #     documentList: documentList
  #     cache: cache
  #   })
  #   documentList2 = new DocumentList(...)
  #   view.setDocumentList(documentList2)
  #
  # Events:
  #
  # * edit-tag: (tagId, a Number) indicates the user requests a tag edit
  # * edit-node: (nodeId, a Number) indicates the user requests a node edit
  Backbone.View.extend
    id: 'document-list-title'

    events:
      'click a.tag-edit': '_onTagEditClicked'
      'click a.node-edit': '_onNodeEditClicked'

    initialize: ->
      throw 'Must supply options.documentList, a DocumentList' if 'documentList' not of @options
      throw 'Must supply options.cache, a Cache' if !@options.cache?

      @documentList = @options.documentList
      @tagStore = @options.cache.tag_store
      @searchResultStore = @options.cache.search_result_store
      @onDemandTree = @options.cache.on_demand_tree

      @_onTagIdChanged = (oldId, tag) =>
        @$(".tag[data-id=#{oldId}]").attr('data-id', tag.id)

      @_onTagChanged = (tag) =>
        if @$(".tag[data-id=#{tag.id}]")
          @render()

      @_onDocumentListChanged = => @render()

      @documentList?.observe(@_onDocumentListChanged)
      @tagStore.observe('id-changed', @_onTagIdChanged)
      @tagStore.observe('changed', @_onTagChanged)

      @render()

    remove: ->
      @documentList?.unobserve(@_onDocumentListChanged)
      @tagStore.unobserve('id-changed', @_onTagIdChanged)
      @tagStore.unobserve('changed', @_onTagChanged)
      Backbone.View.prototype.remove.call(this)

    render: ->
      html = if @documentList
        nDocuments = @documentList.n

        if nDocuments?
          selection = @documentList.selection
          nNodes = selection.nodes.length
          nTags = selection.tags.length
          nSearchResults = selection.searchResults.length

          if nTags + nNodes + nSearchResults == 1
            if nTags
              tagId = selection.tags[0]
              tag = @tagStore.find_by_id(tagId)
              templates.tag({ t: t, nDocuments: nDocuments, tag: tag })
            else if nNodes
              nodeId = selection.nodes[0]
              node = @onDemandTree.nodes[nodeId]
              templates.node({ t: t, nDocuments: nDocuments, node: node })
            else # nSearchResults
              searchResultId = selection.searchResults[0]
              searchResult = @searchResultStore.find_by_id(searchResultId)
              templates.searchResult({ t: t, nDocuments: nDocuments, searchResult: searchResult })
          else
            templates.multiple({ t: t, nDocuments: nDocuments })
        else
          templates.loading({ t: t })
      else
        ''

      @$el.html(html)

    _onTagEditClicked: (e) ->
      e.preventDefault()

      tagId = parseInt($(e.currentTarget).closest('[data-id]').attr('data-id'), 10)
      @trigger('edit-tag', tagId)

    _onNodeEditClicked: (e) ->
      e.preventDefault()

      nodeId = parseInt($(e.currentTarget).closest('[data-id]').attr('data-id'), 10)
      @trigger('edit-node', nodeId)

    setDocumentList: (documentList) ->
      @documentList?.unobserve(@_onDocumentListChanged)
      @documentList = documentList
      @documentList?.observe(@_onDocumentListChanged)
      @render()
