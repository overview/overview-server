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
        serverUrlFromPlugin: null

      @el = document.createElement('div')

    afterEach ->
      @viewApp.remove()

    it 'should show an iframe', ->
      @viewApp = new ViewApp
        documentSetId: 234
        el: @el
        view: @view

      $iframe = @viewApp.$('iframe')
      expect($iframe.length).to.exist
      expect($iframe).to.have.attr('src', 'https://example.org/show?server=http%3A%2F%2Flocalhost%3A9876&documentSetId=234&apiToken=api-token')

    it 'should use serverUrlFromPlugin when set', ->
      @view.set(serverUrlFromPlugin: 'http://overview-web')
      @viewApp = new ViewApp
        documentSetId: 234
        el: @el
        view: @view

      $iframe = @viewApp.$('iframe')
      expect($iframe).to.have.attr('src', 'https://example.org/show?server=http%3A%2F%2Foverview-web&documentSetId=234&apiToken=api-token')
