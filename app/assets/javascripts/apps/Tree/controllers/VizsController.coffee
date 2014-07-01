define [
  'jquery'
  '../collections/Vizs'
  '../views/VizTabs'
  'apps/ImportOptions/app'
], ($, Vizs, VizTabs, OptionsApp) ->
  class VizsController
    constructor: (@vizs, @state) ->
      @view = new VizTabs
        collection: @vizs
        state: @state
      @view.render()

      @view.on('click', (viz) => @state.setViz(viz))

      onSubmit = (data) =>
        # Add a placeholder job so pollUntilStable will actually send an
        # initial poll. When the server responds to the poll, this will
        # disappear and the real job will appear instead.
        @vizs.unshift(id: 0, type: 'job', title: data?[0]?.value, progress: {})
        @vizs.pollUntilStable()

      @view.on 'cancel', (job) =>
        jobId = job.get('id')
        $.ajax
          type: 'delete'
          url: "/trees/jobs/#{jobId}"
          complete: => @vizs.pollUntilStable()

      @view.on 'click-new', ->
        m = /\/documentsets\/([^\/]+)\//.exec(document.location.pathname)
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

      @el = @view.el
