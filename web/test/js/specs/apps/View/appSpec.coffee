define [
  'backbone'
  'apps/View/app'
], (Backbone, ViewApp) ->
  describe 'apps/View/app', ->
    class View extends Backbone.Model

    beforeEach ->
      @view = new View
        type: 'view'
        id: '123'
        apiToken: 'api-token'
        title: 'title'
        url: 'https://example.org'

      @el = document.createElement('div')

      @viewApp = new ViewApp
        documentSetId: 234
        el: @el
        view: @view

    afterEach ->
      @viewApp.remove()

    it 'should show an iframe', ->
      $iframe = @viewApp.$('iframe')
      expect($iframe.length).to.exist
      expect($iframe).to.have.attr('src', 'https://example.org/show?server=http%3A%2F%2Flocalhost%3A9876&documentSetId=234&apiToken=api-token')
