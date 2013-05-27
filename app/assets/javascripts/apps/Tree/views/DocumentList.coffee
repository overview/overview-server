define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.DocumentList.#{key}", args...)

  templates =
    # We put < and > at the exact beginning and end of screen so we can iterate
    # over DOM children without bothering with text nodes
    model: _.template("""<li <%= liAttributes %>
      class="document" data-cid="<%- model.cid %>" data-docid="<%- model.id %>">
        <h3><%- attrs.title ? t('title', attrs.title) : t('title.empty') %></h3>
        <p class="description"><%- attrs.description ? t('description', attrs.description) : t('description.empty') %></p>
        <ul class="tags">
          <% _.each(tags, function(tag) { %>
            <li class="tag" data-cid="<%- tag.cid %>">
              <div class="tag" style="background-color: <%= tag.get('color') %>;">
                <span class="name"><%- tag.get('name') %></span>
                <a class="remove" href="#" title="<%- t('tag.remove.title') %>"><%- t('tag.remove') %></a>
              </div>
            </li>
          <% }); %>
        </ul>
      </li
    >""")

    placeholderModel: _.template("""<li <%= liAttributes %>
      class="document placeholder" data-cid="<%- model.cid %>">
        <h3><%- t('placeholder') %></h3>
        <p class="description"><%- t('placeholder') %></p>
        <ul class="tags"></ul>
      </li
    >""")

    loadingModel: _.template("""<li <%= liAttributes %>
      class="document loading" data-cid="<%- model.cid %>">
        <h3><%- t('loading') %></h3>
        <p class="description"><%- t('loading') %></p>
        <ul class="tags"></ul>
      </li
    >""")

    collection: _.template("""
      <ul class="documents" <%= ulAttributes %>><%= _.map(collection.models, renderModel).join('') %></ul>
    """)

  # Shows a list of Document models.
  #
  # Each document has a list of tags.
  #
  # The following events can be fired:
  #
  # * remove-tag(document, tag): user requested to remove a tag from a document
  # * click-document(document, index, { shift: boolean, meta: boolean }): user clicked a document
  #
  # The following options must be passed:
  #
  # * collection
  # * tags
  # * tagIdToModel (see TagStoreProxy for a good implementation)
  # * selection (a Backbone.Model with 'cursorIndex' and 'selectedIndices')
  #
  # The following options are, well, optional:
  #
  # * liAttributes: string of HTML attributes for document li tags. This is only
  #   needed for testing, as the browser (Chrome 26.0.1410.63 on Ubuntu)
  #   doesn't seem to repaint after we change elements' heights and before
  #   we run calculations with the new heights.
  # * ulAttributes: ditto, but for the outer ul
  Backbone.View.extend
    id: 'document-list'

    events:
      'mousedown': '_onMousedownCancelSelect'
      'click .tag a.remove': '_onClickTagRemove'
      'click .document': '_onClickDocument'

    initialize: ->
      throw 'Must pass options.collection, a Collection of Backbone.Models' if !@options.collection
      throw 'Must pass options.tags, a Collection of Backbone.Tags' if !@options.tags
      throw 'Must pass options.tagIdToModel, a function mapping id to Backbone.Model' if !@options.tagIdToModel
      throw 'Must pass options.selection, a Backbone.Model with cursorIndex and selectedIndices' if !@options.selection

      @tagIdToModel = @options.tagIdToModel

      @_listenToCollection(@collection)

      @listenTo(@options.tags, 'change', (model) => @_renderTag(model))
      @listenTo(@options.selection, 'change:selectedIndices', (model, value) => @_renderSelectedIndices(value))
      @listenTo(@options.selection, 'change:cursorIndex', (model, value) => @_renderCursorIndex(value))
      @$el.on('scroll', => @_updateMaxViewedIndex())
      @render()
      @_renderSelectedIndices(@options.selection.get('selectedIndices'))
      @_renderCursorIndex(@options.selection.get('cursorIndex'))

    _listenToCollection: (collection) ->
      @listenTo(collection, 'reset', => @render())
      @listenTo(collection, 'change', (model) => @_changeModel(model))
      @listenTo(collection, 'add', (model, collection, options) => @_addModel(model, options))

    setCollection: (collection) ->
      @stopListening(@collection)
      @collection = collection
      @_listenToCollection(@collection)
      @render()

    _renderModelHtml: (model, index) ->
      template = if !model.id?
        if index == @options.collection.length - 1
          templates.loadingModel
        else
          templates.placeholderModel
      else
        templates.model

      # Sort tagids
      tagidSet = {}
      tagidSet[id] = true for id in model.attributes.tagids || []
      tags = @options.tags.filter((tag) -> tag.id of tagidSet)

      template({
        model: model
        attrs: model.attributes
        tags: tags
        t: t
        liAttributes: @options.liAttributes || ''
      })

    render: ->
      html = if @collection.length
        html = templates.collection({
          collection: @collection
          t: t
          renderModel: (model, index) => @_renderModelHtml(model, index)
          ulAttributes: @options.ulAttributes || ''
        })
      else
        ''

      @$el.html(html)

      delete @maxViewedIndex
      @_updateMaxViewedIndex()

    # Returns the maximum index of a ul.documents>li that is presently visible.
    #
    # Assumes the ul.documents is entirely on-screen.
    _findMaxViewedIndex: (startAt) ->
      ul = @$('ul.documents')[0]

      return undefined if !ul?

      elBottom = (el) -> el.getBoundingClientRect().bottom

      containerBottom = elBottom(@el)

      lis = ul.childNodes
      i = startAt || 0

      while i < lis.length && elBottom(lis[i]) < containerBottom
        i += 1

      i

    _updateMaxViewedIndex: ->
      maxViewedIndex = @_findMaxViewedIndex(@maxViewedIndex || 0)
      if !@maxViewedIndex? || maxViewedIndex > @maxViewedIndex
        @maxViewedIndex = maxViewedIndex
        @trigger('change:maxViewedIndex', this, maxViewedIndex)

    _addModel: (model, options) ->
      $ul = @$('ul.documents')

      if !$ul.length
        @render()
      else
        $lis = $ul.children()

        index = options?.at || $lis.length

        html = @_renderModelHtml(model)

        if index >= $lis.length
          $ul.append(html)
        else
          $lis.eq(index).before(html)

    _changeModel: (model) ->
      html = @_renderModelHtml(model)
      $li = @$("li.document[data-cid=#{model.cid}]")
      className = $li[0].className # selected/cursor
      $newLi = $(html)
      $newLi[0].className = className
      $li.replaceWith($newLi)

    _renderTag: (tag) ->
      $tags = @$(".tag[data-cid=#{tag.cid}]>div")
      $tags.find('.name').text(tag.get('name'))
      $tags.css('background-color', tag.get('color'))

    _renderSelectedIndices: (selectedIndices) ->
      @$('ul.documents>li.selected').removeClass('selected')
      if selectedIndices?.length
        $lis = @$('ul.documents>li')
        for index in selectedIndices
          $lis.eq(index).addClass('selected')

      if selectedIndices?.length == 1
        @$el.addClass('document-selected')
      else
        @$el.removeClass('document-selected')

    _renderCursorIndex: (index) ->
      @$('ul.documents>li.cursor').removeClass('cursor')
      if index?
        $li = @$("li.document:eq(#{index})")

        margin = 20 # always scroll this number of pixels past what we strictly need

        height = @$el.height()
        currentTop = @$el.scrollTop()
        currentBottom = currentTop + height
        neededTop = Math.max(0, ($li.position().top - $li.parent().position().top) - margin)
        neededBottom = Math.max(height, neededTop + $li.outerHeight() + margin + margin)

        if currentTop > neededTop
          # scroll up
          @$el.scrollTop(neededTop)
        else if currentBottom < neededBottom
          # scroll down
          @$el.scrollTop(neededBottom - height) # neededBottom >= height

        $li.addClass('cursor')


    _onMousedownCancelSelect: (e) ->
      e.preventDefault() if e.ctrlKey || e.metaKey || e.shiftKey

    _onClickTagRemove: (e) ->
      e.preventDefault()
      $target = $(e.target)
      tagCid = $target.closest('.tag[data-cid]').attr('data-cid')
      documentCid = $target.closest('.document[data-cid]').attr('data-cid')
      tag = @options.tags.get(tagCid)
      document = @collection.get(documentCid)
      @trigger('remove-tag', document, tag)

    _onClickDocument: (e) ->
      e.preventDefault()
      $document = $(e.target).closest('.document[data-cid]')
      document = @collection.get($document.attr('data-cid'))
      index = $document.prevAll().length
      @trigger('click-document', document, index, {
        meta: e.ctrlKey || e.metaKey || false
        shift: e.shiftKey || false
      })
