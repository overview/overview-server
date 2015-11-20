define [
  'underscore'
  'jquery'
  'backbone'
  '../collections/Plugins'
  '../views/ViewTabs'
  '../views/NewTreeDialog'
  '../views/NewViewDialog'
  'i18n'
], (_, $, Backbone, Plugins, ViewTabs, NewTreeDialog, NewViewDialog, i18n) ->
  class ViewsController
    _.extend(@::, Backbone.Events)

    constructor: (@views, @state) ->
      @plugins = new Plugins([])
      @plugins.fetch(reset: true)

      @view = new ViewTabs
        collection: @views
        plugins: @plugins
        state: @state
      @view.render()

      @listenTo(@view, 'click', @_onClickView)
      @listenTo(@view, 'delete-view', @_onDelete)
      @listenTo(@view, 'update-view', @_onUpdate)
      @listenTo(@view, 'click-new-tree', @_onClickNewTree)
      @listenTo(@view, 'click-new-view', @_onClickNewView)
      @listenTo(@views, 'add', @_onAdd)

      @el = @view.el

    _onClickView: (view) -> @state.setView(view)
    _onAdd: (view) -> @state.setView(view)

    _onDelete: (view) ->
      view.set(deleting: true)
      view.destroy
        wait: true
        success: => @state.setView(@views.at(0) || null)

    _onUpdate: (view, attrs) ->
      view.save(attrs)

    _onClickNewTree: ->
      dialog = new NewTreeDialog
        supportedLanguages: window.supportedLanguages
        defaultLanguageCode: window.defaultLanguageCode
        tags: @state.documentSet.tags
        submit: (queryString) =>
          data = "#{queryString}&#{window.csrfTokenQueryString}"
          @state.transactionQueue.ajax
            type: 'post'
            url: "/documentsets/#{@state.documentSet.id}/trees"
            data: data
            success: (tree) =>
              dialog.$el.modal('hide')
              @views.add([ tree ])
              @views.pollUntilStable()

    _onClickNewView: (options) ->
      addView = (attrs) =>
        $.ajax
          type: 'post'
          data: attrs
          url: "/documentsets/#{@state.documentSetId}/views"
          success: (json) =>
            view = @views.add(json)[0]
            @state.setView(view) if view?
          error: console.log.bind(console, 'Server error creating view')

      if options?.url && options?.title
        addView(options)
      else
        new NewViewDialog
          url: options?.url
          success: addView
