define [
  'underscore'
  'backbone'
  '../helpers/DocumentHelper'
  'i18n'
], (_, Backbone, DocumentHelper, i18n) ->
  t = i18n.namespaced('views.Tree.show.DocumentList')

  templates =
    # We put < and > at the exact beginning and end of screen so we can iterate
    # over DOM children without bothering with text nodes
    model: _.template("""<li <%= liAttributes %>
      class="document" data-cid="<%- model.cid %>" data-docid="<%- model.id %>">
        <h3><%- title %></h3>
        <p class="description"><%- attrs.description ? t('description', attrs.description) : t('description.empty') %></p>
        <ul class="tags">
          <% _.each(tags, function(tag) { %>
            <li class="tag" data-cid="<%- tag.cid %>">
              <div class="<%= tag.getClass() %>" style="<%= tag.getStyle() %>">
                <span class="name"><%- tag.get('name') %></span>
                <a class="remove" href="#" title="<%- t('tag.remove') %>">&times;</a>
              </div>
            </li>
          <% }); %>
        </ul>
      </li
    >""")

    collection: _.template("""
      <ul class="documents" <%= ulAttributes %>><%= _.map(collection.models, renderModel).join('') %></ul>
      <div class="loading"><%- t('loading') %></div>
    """)

    error: _.template("""
      <div class="error"><%- error %></div>
    """)

  # Shows a list of Document models.
  #
  # Each document has a list of tags.
  #
  # The following events can be fired:
  #
  # * tag-remove-clicked(tagCid: cid, documentId: id): user requested to remove a tag from a document
  # * click-document(document, index, { shift: boolean, meta: boolean }): user clicked a document
  #
  # The following options must be passed:
  #
  # * collection
  # * tags
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
      throw 'Do not pass options.collection' if @collection?
      throw 'Must pass options.model, a DocumentList' if !@model? || !@model.documents?
      throw 'Must pass options.tags, a Collection of Backbone.Tags' if !@options.tags
      throw 'Must pass options.selection, a Backbone.Model with cursorIndex and selectedIndices' if !@options.selection

      @listenTo(@options.tags, 'change', @_renderTag)
      @listenTo(@options.selection, 'change:selectedIndices', (model, value) => @_renderSelectedIndices(value))
      @listenTo(@options.selection, 'change:cursorIndex', (model, value) => @_renderCursorIndex(value))
      @$el.on('scroll', => @_updateMaxViewedIndex())

      @setModel(@model) # calls @render()
      @_renderSelectedIndices(@options.selection.get('selectedIndices'))
      @_renderCursorIndex(@options.selection.get('cursorIndex'))

    _listenToModel: ->
      @listenTo(@model, 'change:error', @render)
      @listenTo(@model, 'change:loading', @_renderLoading)
      @listenTo(@collection, 'reset', @render)
      @listenTo(@collection, 'change', @_changeModel)
      @listenTo(@collection, 'remove', @_removeModel)
      @listenTo(@collection, 'add', @_addModel)
      @listenTo(@collection, 'tag', @_renderAllTags)
      @listenTo(@collection, 'untag', @_renderAllTags)

    setModel: (model) ->
      @stopListening(@model) if @model?
      @stopListening(@collection) if @collection?
      @model = model
      @collection = model.documents
      @_listenToModel()
      @render()

    _renderModelHtml: (model) ->
      tags = @options.tags.filter((x) -> model.hasTag(x))

      templates.model
        title: DocumentHelper.title(model.attributes)
        model: model
        attrs: model.attributes
        tags: tags
        t: t
        liAttributes: @options.liAttributes || ''

    render: ->
      html = if @model.get('error')
        templates.error(error: @model.get('error'), t: t)
      else
        templates.collection({
          collection: @collection
          t: t
          renderModel: (model) => @_renderModelHtml(model)
          ulAttributes: @options.ulAttributes || ''
        })

      @$el.html(html)

      @_$els =
        ul: @$('ul.documents')

      delete @maxViewedIndex
      @_updateMaxViewedIndex()
      @_renderLoading()

    _renderLoading: ->
      loading = @model.get('loading')
      @$el.toggleClass('loading', loading)

    _renderAllTags: (tag) ->
      # The tag has been added to or removed from lots of documents -- probably
      # all of them have changed. But we don't want to reset the list, because
      # that would break scrolling, cursor and selection. So we replace one at
      # a time.
      @_changeModel(model) for model in @collection.models
      undefined

    # Returns the maximum index of a ul.documents>li that is presently visible.
    #
    # Assumes the ul.documents is entirely on-screen.
    _findMaxViewedIndex: (startAt) ->
      ul = @_$els.ul[0]

      return undefined if !ul?

      elBottom = (el) -> el.getBoundingClientRect().bottom

      containerBottom = elBottom(@el)

      lis = ul.childNodes

      return -1 if lis.length == 0

      # Binary search
      low = startAt || 0 # invariant: low is always fully visible
      high = lis.length - 1 # invariant: high+1 is always invisible, at least partially

      while low < high
        mid = (low + high + 1) >>> 1 # low < mid <= high
        if elBottom(lis[mid]) < containerBottom
          low = mid
        else
          high = mid - 1

      # low=high, so low is visible and low+1 is partially invisible

      Math.min(low + 1, lis.length - 1)

    _updateMaxViewedIndex: ->
      maxViewedIndex = @_findMaxViewedIndex(@maxViewedIndex || 0)
      if !@maxViewedIndex? || maxViewedIndex > @maxViewedIndex
        @maxViewedIndex = maxViewedIndex
        @trigger('change:maxViewedIndex', this, maxViewedIndex)

    _addModel: (model) ->
      if @_$els.ul.length == 0
        @render()
      else
        html = @_renderModelHtml(model)
        @_$els.ul.append(html)

    _removeModel: (model) ->
      $li = @$("li.document[data-cid=#{model.cid}]")
      $li.remove()

    _changeModel: (model) ->
      $li = @$("li.document[data-cid=#{model.cid}]")

      html = @_renderModelHtml(model)
      $newLi = $(html)
      $newLi.addClass('selected') if $li.hasClass('selected')
      $newLi.addClass('cursor') if $li.hasClass('cursor')

      $li.replaceWith($newLi)

    _renderTag: (tag) ->
      if 'name' of tag.changed
        # Tags might be reordered. Rewrite all models.
        $documents = @$(".tag[data-cid=#{tag.cid}]").closest('li.document')
        for documentEl in $documents
          documentCid = documentEl.getAttribute('data-cid')
          document = @collection.get(documentCid)
          @_changeModel(document)
      else
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
      e.stopPropagation() # skip _onClickDocument
      $target = $(e.target)
      tagCid = $target.closest('.tag[data-cid]').attr('data-cid')
      documentCid = $target.closest('.document[data-cid]').attr('data-cid')
      document = @collection.get(documentCid)
      @trigger('tag-remove-clicked', tagCid: tagCid, documentId: document.id)

    _onClickDocument: (e) ->
      e.preventDefault()
      $document = $(e.target).closest('.document[data-cid]')
      document = @collection.get($document.attr('data-cid'))
      index = $document.prevAll().length
      @trigger('click-document', document, index, {
        meta: e.ctrlKey || e.metaKey || false
        shift: e.shiftKey || false
      })
