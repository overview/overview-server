define [
  'jquery'
  '../collections/Vizs'
  '../views/VizTabs'
  'apps/ImportOptions/app'
], ($, Vizs, VizTabs, OptionsApp) ->
  class VizsController
    constructor: (vizsJson) ->
      vizs = @vizs = new Vizs()

      refreshTimeout = null

      refresh = ->
        refreshTimeout = null
        $.ajax
          type: 'get'
          url: "#{urlParts[0...-2].join('/')}/vizs.json"
          success: handleJson

      handleJson = (json) ->
        vizs.set(json)
        if 'job' in vizs.pluck('type')
          refreshTimeout ||= setTimeout(refresh, 1000)

      handleJson(vizsJson)

      urlParts = document.location.pathname.split('/')
      selectedVizId = parseInt(urlParts[urlParts.length - 1], 10)
      selectedViz = @vizs.get("viz-#{selectedVizId}")
      @view = new VizTabs
        collection: @vizs
        selected: selectedViz
      @view.render()

      @view.on 'click', (viz) ->
        vizId = viz.get('id')
        if vizId != selectedVizId
          newPath = document.location.pathname.replace(/[^/]*$/, vizId)
          document.location = newPath

      @view.on 'cancel', (job) ->
        jobId = job.get('id')
        $.ajax
          type: 'delete'
          url: "/trees/jobs/#{jobId}"
          complete: refresh

      @view.on 'click-new', ->
        m = /\/documentsets\/([^\/]+)\//.exec(document.location.pathname)
        tagListUrl = "/documentsets/#{m[1]}/tags.json"
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
            complete: refresh

      @el = @view.el
