require [
  'for-view/DocumentSet/_show/models/document_store'
], (DocumentStore) ->
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
        document = { id: 1, description: 'foo' }
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

      it 'should notify :document-added', ->
        document1 = { id: 1, title: 'foo' }
        v = undefined
        store.observe('document-added', (o) -> v = o)
        store.add(document1)
        expect(v).toBe(document1)

      it 'should notify :document-added even when the same document is re-added', ->
        document1 = { id: 1, title: 'foo' }
        store.add(document1)
        v = undefined
        store.observe('document-added', (o) -> v = o)
        document1.title = 'bar'
        store.add(document1)
        expect(v).toBe(document1)

      it 'should notify :document-changed', ->
        document1 = { id: 1, title: 'foo' }
        store.add(document1)
        v = undefined
        store.observe('document-changed', (o) -> v = o)
        store.change(document1)
        expect(v).toBe(document1)

      it 'should notify :document-removed', ->
        document = { id: 1, title: 'foo' }
        store.add(document)
        v = undefined
        store.observe('document-removed', (o) -> v = o)
        store.remove(document)
        expect(v).toBe(document)

      it 'should notify :document-removed even when the document is still there', ->
        document = { id: 1, title: 'foo' }
        store.add(document)
        store.add(document)
        v = undefined
        store.observe('document-removed', (o) -> v = o)
        store.remove(document)
        expect(v).toBe(document)

      it 'should rewrite_tag_id()', ->
        document = { id: 1, title: 'foo', tagids: [ -1 ] }
        store.add(document)
        store.rewrite_tag_id(-1, 3)
        expect(document.tagids).toEqual([3])

      it 'should remove_tag_id()', ->
        document = { id: 1, title: 'foo', tagids: [ 1 ] }
        store.add(document)
        store.remove_tag_id(1)
        expect(document.tagids).toEqual([])
