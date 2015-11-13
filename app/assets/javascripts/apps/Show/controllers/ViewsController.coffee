define [
  'underscore'
  'jquery'
  'backbone'
  '../collections/Plugins'
  '../views/ViewTabs'
  '../views/NewViewDialog'
  'apps/ImportOptions/app'
], (_, $, Backbone, Plugins, ViewTabs, NewViewDialog, OptionsApp) ->
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
      m = /\/documentsets\/([^\/]+)/.exec(document.location.pathname)
      tagListUrl = "/documentsets/#{m[1]}/tags"
      submitUrl = "/documentsets/#{m[1]}/trees"

      $dialog = OptionsApp.createNewTreeDialog
        supportedLanguages: window.supportedLanguages
        defaultLanguageCode: window.defaultLanguageCode
        onlyOptions: [ 'tree_title', 'tag_id', 'lang', 'supplied_stop_words', 'important_words' ]
        tagListUrl: tagListUrl
        url: submitUrl
        csrfToken: window.csrfToken
      $dialog.on 'submit', (e) =>
        e.preventDefault()
        data = $dialog.serializeArray()
        $dialog.find('form').prop('disabled', true)
        $dialog.find('[type=submit]').html('<i class="icon icon-spinner icon-spin"></i>')

        @state.transactionQueue.ajax
          type: 'post'
          url: submitUrl
          data: data
          success: (tree) =>
            $dialog.modal('hide')
            console.log(tree)
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
