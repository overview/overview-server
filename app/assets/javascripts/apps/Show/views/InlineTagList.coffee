define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.InlineTagList')

  # A list of inline tags, with controls for removing them.
  #
  # Events:
  #
  # * name-clicked(tag): A tag was clicked
  # * remove-clicked(tag): A tag "remove" button was clicked
  Backbone.View.extend
    id: 'tag-list'

    events:
      'click a.tag-name': '_onClickName'
      'click a.tag-remove': '_onClickRemove'
      'click a.organize': '_onClickOrganize'
      'click a.untagged': '_onClickUntagged'
      'submit form': '_onSubmit'

    templates:
      model: _.template("""
        <li
            class="btn-group"
            data-cid="<%- model.cid %>"
            data-id="<%- model.id %>"
            style="background-color:<%- model.get('color') %>">
          <a class="btn tag-name"><%- model.get('name') %></a>
          <a class="btn tag-remove" title="<%- t('remove') %>"><i class="overview-icon-minus"></i></a>
        </li>
      """)

      collection: _.template("""
        <div class="label">Tags</div>
        <ul class="btn-toolbar">
          <%= collection.map(renderModel).join('') %>
          <li class="btn-group untagged">
            <a class="btn untagged" href="#"><%- t('show_untagged') %></a>
          </li>
          <li class="btn-group">
            <a class="btn organize" href="#"><%- t('organize') %></a>
          </li>
        </ul>
      """)

    initialize: ->
      throw 'Must set collection, a Backbone.Collection of tag models' if !@collection
      throw 'Must set options.state, a State' if !@options.state

      @state = @options.state

      @listenTo(@state, 'change:documentListParams', @_onChangeDocumentListParams)
      for k, v of { change: @_onChange, add: @_onAdd, remove: @_onRemove, reset: @_onReset, sort: @_onReset }
        @listenTo(@collection, k, v)
      @render()

    _onChangeDocumentListParams: ->
      @_renderSelected()

    # Adds a 'selected' class to the selection (either a tag or "untagged").
    _renderSelected: ->
      @_renderTagSelected()
      @_renderUntaggedSelected()

    # Adds a 'selected' class to the selected tag's <li>
    _renderTagSelected: ->
      @$('li[data-id].selected').removeClass('selected')
      ids = @state.get('documentListParams')?.params?.tags || []
      @$("li[data-id='#{ids[0]}']").addClass('selected') if ids.length

      undefined

    # Adds a 'selected' class to li.untagged
    _renderUntaggedSelected: ->
      oldUntagged = Boolean(@$('li.untagged').hasClass('selected'))
      newUntagged = false == @state.get('documentListParams')?.params?.tagged

      return if oldUntagged == newUntagged

      @$('li.untagged').toggleClass('selected', newUntagged)

    _onReset: ->
      @render()

    _onAdd: (model, collection, options) ->
      html = @templates.model
        model: model
        t: t

      $li = $(html)

      at = options.at ? @$('li[data-cid]').length

      @$("li:eq(#{at})").before($li)
      this

    _onRemove: (model) ->
      $li = @$("li[data-cid='#{model.cid}']")
      $li.remove()

    _onChange: (model) ->
      $li = @$("li[data-cid='#{model.cid}']")
      $li.css('background-color': model.get('color'))
      $li.find('.tag-name').text(model.get('name'))
      $li.attr('data-id', model.id)
      @_renderTagSelected() # in case data-id changed
      this

    render: ->
      renderModel = (model) => @templates.model(model: model, t: t)

      html = @templates.collection
        collection: @collection
        renderModel: renderModel
        t: t

      @$el.html(html)
      @_renderSelected()

      this

    _eventToModel: (e) ->
      cid = $(e.currentTarget).closest('[data-cid]').attr('data-cid')
      @collection.get(cid)

    _onEvent: (e, triggerEvent) ->
      e.preventDefault()
      model = @_eventToModel(e)
      @trigger(triggerEvent, model)

    _onClickName: (e) -> @_onEvent(e, 'name-clicked')
    _onClickRemove: (e) -> @_onEvent(e, 'remove-clicked')

    _onClickOrganize: (e) ->
      e.preventDefault()
      @trigger('organize-clicked')

    _onClickUntagged: (e) ->
      e.preventDefault()
      @trigger('untagged-clicked')
