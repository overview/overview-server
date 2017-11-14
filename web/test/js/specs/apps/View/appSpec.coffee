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
        url: 'http://localhost:9876/base/mock-plugin'
        serverUrlFromPlugin: null

      @el = document.createElement('div')
      @main = document.createElement('main')
      @main.innerHTML = [
        '<div id="tree-app-left"></div>',
        '<div id="tree-app-vertical-split"></div>',
        '<div id="tree-app-right"></div>',
        '<div id="tree-app-vertical-split-2"></div>',
        '<div id="tree-app-right-pane"></div>',
      ].join('')

      document.body.appendChild(@el)
      document.body.appendChild(@main)

      @createViewApp = () =>
        @viewApp = new ViewApp
          documentSetId: 234
          el: @el
          main: @main
          view: @view

    afterEach ->
      @viewApp?.remove()
      document.body.removeChild(@main) # we don't want viewApp.remove() to remove this

    it 'should use serverUrlFromPlugin when set', ->
      @view.set(serverUrlFromPlugin: 'http://overview-web')
      @createViewApp()

      $iframe = @viewApp.$('iframe')
      expect($iframe).to.have.attr('src', 'http://localhost:9876/base/mock-plugin/show?server=http%3A%2F%2Foverview-web&documentSetId=234&apiToken=api-token')

    it 'should clear tree-app-right-pane on start', ->
      pane = @main.querySelector('#tree-app-right-pane')
      pane.innerHTML = 'here is some content'
      @createViewApp()
      expect(pane.innerHTML).to.eq('')

    it 'should show an iframe', ->
      @createViewApp()
      $iframe = @viewApp.$('iframe')
      expect($iframe.length).to.exist
      expect($iframe).to.have.attr('src', 'http://localhost:9876/base/mock-plugin/show?server=http%3A%2F%2Flocalhost%3A9876&documentSetId=234&apiToken=api-token')

    describe 'after setRightPane', ->
      beforeEach (callback) ->
        @rightPane = @main.querySelector('#tree-app-right-pane')
        @createViewApp()
        @viewApp.setRightPane({ url: 'http://localhost:9876/base/mock-plugin/right-pane.html' })

        iframe = @main.querySelector('#view-app-right-pane-iframe')

        continueOnceIframeLoads = ->
          if iframe.contentWindow.location.href == 'about:blank'
            setTimeout(continueOnceIframeLoads, 1)
          else
            callback()
        continueOnceIframeLoads()


      it 'should add "has-right-pane" class to main', ->
        expect(@main.className).to.match(/\bhas-right-pane\b/)

      it 'should add an iframe to the right pane', ->
        expect(@rightPane.querySelector('iframe#view-app-right-pane-iframe')).not.to.be.null

      it 'should postMessage to the right-pane iframe', (callback) ->
        iframe = @rightPane.querySelector('iframe')
        iframe.contentWindow.addEventListener 'message', (e) ->
          expect(e.data).to.deep.eq({ event: 'notify:documentListParams', args: [ { foo: 'bar' } ] })
          callback()
        @viewApp.notifyDocumentListParams({ foo: 'bar' })

      it 'should allow setting right pane to blank', ->
        @viewApp.setRightPane({ url: null })
        expect(@main.className).not.to.match(/\bhas-right-pane\b/)
        expect(@rightPane.innerHTML).to.eq('')
        expect(=> @viewApp.notifyDocumentListParams({ foo: 'bar' })).not.to.throw
