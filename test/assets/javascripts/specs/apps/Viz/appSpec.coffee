define [
  'backbone'
  'apps/Viz/app'
], (Backbone, VizApp) ->
  describe 'apps/Viz/app', ->
    class Viz extends Backbone.Model

    beforeEach ->
      @viz = new Viz
        type: 'viz'
        id: '123'
        apiToken: 'api-token'
        title: 'title'
        url: 'https://example.org'

      @el = document.createElement('div')

      @vizApp = new VizApp
        documentSetId: 234
        el: @el
        viz: @viz

    afterEach ->
      @vizApp.remove()

    it 'should show an iframe', ->
      $iframe = @vizApp.$('iframe')
      expect($iframe.length).to.exist
      expect($iframe).to.have.attr('src', 'https://example.org/show?server=http%3A%2F%2Flocalhost%3A9876&documentSetId=234&vizId=123&apiToken=api-token')
