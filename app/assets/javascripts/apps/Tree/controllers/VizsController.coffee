define [
  'jquery'
  '../collections/Vizs'
  '../views/VizTabs'
], ($, Vizs, VizTabs) ->
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

      @el = @view.el
