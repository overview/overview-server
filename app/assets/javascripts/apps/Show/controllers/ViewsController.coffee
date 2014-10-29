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

    constructor: (@documentSet, @views, @state) ->
      @plugins = new Plugins([])
      @plugins.fetch(reset: true)

      @view = new ViewTabs
        documentSet: @documentSet
        collection: @views
        plugins: @plugins
        state: @state
      @view.render()

      @listenTo(@view, 'click', @_onClickView)
      @listenTo(@view, 'cancel', @_onCancel)
      @listenTo(@view, 'click-new-tree', @_onClickNewTree)
      @listenTo(@view, 'click-new-view', @_onClickNewView)
      @listenTo(@views, 'add', @_onAdd)

      @el = @view.el

    _onClickView: (view) -> @state.setView(view)
    _onAdd: (view) ->
      @state.setView(view)

    _onCancel: (job) ->
      @views.remove(job)
      @state.setView(@views.at(0) || null)

      jobId = job.get('id')
      $.ajax
        type: 'delete'
        url: "/trees/jobs/#{jobId}"

    _onClickNewTree: ->
      onSubmit = (data) =>
        # Add a placeholder job so pollUntilStable will actually send an
        # initial poll. When the server responds to the poll, this will
        # disappear and the real job will appear instead.
        @views.add(id: 0, type: 'job', title: data?[0]?.value, progress: {})
        @views.pollUntilStable()

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
      $dialog.on 'submit', (e) ->
        e.preventDefault()
        data = $dialog.serializeArray()
        $dialog.modal('hide')

        $.ajax
          type: 'post'
          url: submitUrl
          data: data
          complete: -> onSubmit(data)

    _onClickNewView: (options) ->
      new NewViewDialog
        url: options?.url
        success: (attrs) =>
          $.ajax
            type: 'post'
            data: attrs
            url: "#{@documentSet.url}/views"
            success: (json) =>
              view = @views.add(json)[0]
              @state.setView(view) if view?
            error: console.log.bind(console, 'Server error creating view')
