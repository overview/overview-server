define [
  'backbone'
  'apps/Show/models/Document'
], (Backbone, Document) ->
  describe 'apps/Show/models/Document', ->
    class Tag extends Backbone.Model
      initialize: ->
        @addToDocumentsOnServer = sinon.spy()
        @removeFromDocumentsOnServer = sinon.spy()

    describe 'parse', ->
      it 'should parse page_number to pageNumber', ->
        document = new Document({ id: 1, page_number: 2 }, parse: true)
        expect(document.get('pageNumber')).to.eq(2)

      it 'should parse a null pageNumber by default', ->
        document = new Document({ id: 1 }, parse: true)
        expect(document.get('pageNumber')).to.be.null

    describe 'with a typical document', ->
      beforeEach ->
        @document = new Document({ id: 1, title: 'title', description: 'a description', tagids: [ 3 ] }, parse: true)

      afterEach ->
        @document.off()

      it 'should not have a tag by default', ->
        tag = new Tag(id: 1)
        expect(@document.hasTag(tag)).to.be.false

      it 'should have a tag if it is in tagIds', ->
        tag = new Tag(id: 3)
        expect(@document.hasTag(tag)).to.be.true

      it 'should tag locally', ->
        tag = new Tag()
        @document.tagLocal(tag)
        expect(@document.hasTag(tag)).to.be.true

      it 'should untag locally', ->
        tag = new Tag(id: 3)
        @document.untagLocal(tag)
        expect(@document.hasTag(tag)).to.be.false

      it 'should tag then untag locally', ->
        tag = new Tag()
        @document.tagLocal(tag)
        @document.untagLocal(tag)
        expect(@document.hasTag(tag)).to.be.false

      it 'should trigger when tagging locally', ->
        tag = new Tag()
        @document.on('document-tagged', spy = sinon.spy())
        @document.tagLocal(tag)
        expect(spy).to.have.been.calledWith(@document, tag)

      it 'should trigger when untagging locally', ->
        tag = new Tag(id: 3)
        @document.on('document-untagged', spy = sinon.spy())
        @document.untagLocal(tag)
        expect(spy).to.have.been.calledWith(@document, tag)

      it 'should not trigger on spurious tagLocal', ->
        tag = new Tag(id: 3)
        @document.on('document-tagged', spy = sinon.spy())
        @document.tagLocal(tag)
        expect(spy).not.to.have.been.called

      it 'should not trigger on spurious untagLocal', ->
        tag = new Tag()
        @document.on('document-untagged', spy = sinon.spy())
        @document.untagLocal(tag)
        expect(spy).not.to.have.been.called
