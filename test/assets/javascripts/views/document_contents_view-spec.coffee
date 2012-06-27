observable = require('models/observable').observable # a small unit-testing transgression
DocumentContentsView = require('views/document_contents_view').DocumentContentsView

class MockSelection
  observable(this)

  constructor: () ->
    @documents = []

class MockRouter
  route_to_path: (key, id) ->
    @last_path = "/#{key}/#{id}"


describe 'views/document_contents_view', ->
  describe 'DocumentContentsView', ->
    selection = undefined
    router = undefined
    document = { id: 1, title: "doc 1" }
    document2 = { id: 2, title: "doc 2" }
    div = undefined
    view = undefined

    create_view = () -> new DocumentContentsView(div, selection, router)
    
    beforeEach ->
      selection = new MockSelection()
      router = new MockRouter()
      div = $('<div></div>')[0]
      $('body').append(div)
      view = undefined

    afterEach ->
      selection = undefined
      router = undefined
      $(div).remove() # Removes all callbacks
      div = undefined
      view = undefined

    describe 'beginning empty', ->
      beforeEach ->
        view = create_view()

      it 'should be empty when there is no selected document', ->
        expect($(view.div).children().length).toEqual(0)

      it 'should build an iframe when there is a selected document', ->
        selection.documents = [ document ]
        selection._notify()
        expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

      it 'should show the first document when several are selected', ->
        selection.documents = [ document, document2 ]
        selection._notify()
        expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

    describe 'beginning on a document', ->
      beforeEach ->
        selection.documents = [ document ]
        view = create_view()

      it 'should show the iframe', ->
        expect($(view.div).find('iframe[src="/document_view/1"]').length).toEqual(1)

      it 'should change the URL when the document changes', ->
        selection.documents = [ document2 ]
        selection._notify()
        $iframe = $(view.div).find('iframe')
        expect($iframe.attr('src')).toEqual('/document_view/2')

      it 'should not change the URL when the selection changes but the selected document does not', ->
        $iframe = $(view.div).find('iframe')
        $iframe.attr('src', '/somewhere-else')
        selection._notify()
        expect($iframe.attr('src')).toEqual('/somewhere-else')
