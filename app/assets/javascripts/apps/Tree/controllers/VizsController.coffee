define [
  'jquery'
  '../collections/Vizs'
  '../views/VizTabs'
  'apps/ImportOptions/app'
], ($, Vizs, VizTabs, OptionsApp) ->
  class VizsController
    constructor: (vizsJson) ->
      @vizs = new Vizs(vizsJson)
      urlParts = document.location.pathname.split('/')
      selectedVizId = parseInt(urlParts[urlParts.length - 1], 10)
      selectedViz = @vizs.get(selectedVizId)
      @view = new VizTabs
        collection: @vizs
        selected: selectedViz
      @view.render()

      @view.on 'click', (viz) ->
        vizId = viz.id
        if vizId != selectedVizId
          newPath = document.location.pathname.replace(/[^/]*$/, vizId)
          document.location = newPath

      @view.on 'click-new', ->
        m = /\/documentsets\/([^\/]+)\//.exec(document.location.pathname)
        tagListUrl = "/documentsets/#{m[1]}/tags.json"
        submitUrl = "/documentsets/#{m[1]}/trees"

        OptionsApp.createNewTreeDialog
          supportedLanguages: window.supportedLanguages
          defaultLanguageCode: window.defaultLanguageCode
          onlyOptions: [ 'tree_title', 'tag_id', 'lang', 'supplied_stop_words', 'important_words' ]
          tagListUrl: tagListUrl
          url: submitUrl
          csrfToken: window.csrfToken

      @el = @view.el
