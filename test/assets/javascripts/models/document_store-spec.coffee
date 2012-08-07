DocumentStore = require('models/document_store').DocumentStore

describe 'models/document_store', ->
  describe 'DocumentStore', ->
    store = undefined

    beforeEach ->
      store = new DocumentStore()

    afterEach ->
      store = undefined

    it 'should start empty', ->
      expect(store.documents).toEqual({})

    it 'should store a document based on id', ->
      document = { id: 1, title: 'foo' }
      store.add(document)
      document2 = store.documents[document.id]
      expect(document2).toBe(document)

    it 'should delete an item on remove()', ->
      document = { id: 1, title: 'foo' }
      store.add(document)
      store.remove(document)
      document2 = store.documents[document.id]
      expect(document2).toBeUndefined()

    it 'should not delete an item that has been added twice', ->
      document = { id: 1, title: 'foo' }
      store.add(document)
      store.add(document)
      store.remove(document)
      document2 = store.documents[document.id]
      expect(document2).toBe(document)

    it 'should add() items from a doclist', ->
      document1 = { id: 1, title: 'foo' }
      document2 = { id: 2, title: 'bar' }
      spyOn(store, 'add')
      store.add_doclist({ n: 2, docids: [ 1, 2 ] }, { "1": document1, "2": document2 })
      expect(store.add).toHaveBeenCalledWith(document1)
      expect(store.add).toHaveBeenCalledWith(document2)

    it 'should remove() items from a doclist', ->
      document1 = { id: 1, title: 'foo' }
      document2 = { id: 2, title: 'bar' }
      store.add(document1)
      store.add(document2)
      spyOn(store, 'remove')
      store.remove_doclist({ n: 2, docids: [ 1, 2 ] })
      expect(store.remove).toHaveBeenCalledWith(document1)
      expect(store.remove).toHaveBeenCalledWith(document2)
