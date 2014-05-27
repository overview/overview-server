define [
  'apps/Tree/models/document_store'
], (DocumentStore) ->
  describe 'models/document_store', ->
    describe 'DocumentStore', ->
      store = undefined

      beforeEach ->
        store = new DocumentStore()

      afterEach ->
        store = undefined

      it 'should start empty', ->
        expect(store.documents).to.deep.eq({})

      it 'should store a document based on id', ->
        document = { id: 1, description: 'foo' }
        store.reset([document])
        document2 = store.documents[document.id]
        expect(document2).to.be(document)

      it 'should notify :document-changed', ->
        document1 = { id: 1, title: 'foo' }
        store.reset([document1])
        v = undefined
        store.observe('document-changed', (o) -> v = o)
        store.change(document1)
        expect(v).to.be(document1)

      it 'should rewrite_tag_id()', ->
        document = { id: 1, title: 'foo', tagids: [ -1 ] }
        store.reset([document])
        store.rewrite_tag_id(-1, 3)
        expect(document.tagids).to.deep.eq([3])

      it 'should remove_tag_id()', ->
        document = { id: 1, title: 'foo', tagids: [ 1 ] }
        store.reset([document])
        store.remove_tag_id(1)
        expect(document.tagids).to.deep.eq([])
