define [
  'underscore'
  'jquery'
  'backbone'
  '../collections/Plugins'
  '../views/VizTabs'
  '../views/NewVizDialog'
  'apps/ImportOptions/app'
], (_, $, Backbone, Plugins, VizTabs, NewVizDialog, OptionsApp) ->
  class VizsController
    _.extend(@::, Backbone.Events)

    constructor: (@documentSet, @vizs, @state) ->
      @plugins = new Plugins([])
      @plugins.fetch(reset: true)

      @view = new VizTabs
        documentSet: @documentSet
        collection: @vizs
        plugins: @plugins
        state: @state
      @view.render()

      @listenTo(@view, 'click', @_onClickViz)
      @listenTo(@view, 'cancel', @_onCancel)
      @listenTo(@view, 'click-new-tree', @_onClickNewTree)
      @listenTo(@view, 'click-new-viz', @_onClickNewViz)
      @listenTo(@vizs, 'add', @_onAdd)

      @el = @view.el

    _onClickViz: (viz) -> @state.setViz(viz)
    _onAdd: (viz) ->
      @state.setViz(viz)

    _onCancel: (job) ->
      @vizs.remove(job)
      @state.setViz(@vizs.at(0) || null)

      jobId = job.get('id')
      $.ajax
        type: 'delete'
        url: "/trees/jobs/#{jobId}"

    _onClickNewTree: ->
      onSubmit = (data) =>
        # Add a placeholder job so pollUntilStable will actually send an
        # initial poll. When the server responds to the poll, this will
        # disappear and the real job will appear instead.
        @vizs.add(id: 0, type: 'job', title: data?[0]?.value, progress: {})
        @vizs.pollUntilStable()

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

    _onClickNewViz: (options) ->
      new NewVizDialog
        url: options?.url
        success: (attrs) =>
          $.ajax
            type: 'post'
            data: attrs
            url: "#{@documentSet.url}/vizs"
            success: (json) =>
              viz = @vizs.add(json)[0]
              @state.setViz(viz) if viz?
            error: console.log.bind(console, 'Server error creating viz')
