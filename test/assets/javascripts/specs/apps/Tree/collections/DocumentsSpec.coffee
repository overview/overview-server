define [
  'apps/Tree/collections/Documents'
], (Documents) ->
  describe 'apps/Tree/collections/Documents', ->
    beforeEach ->
      @documents = new Documents()

    afterEach ->
      @documents.off()

    it 'should parse tagids to tagIds', ->
      @documents.add([{ id: 1, tagids: [ 1, 2, 3 ] } ], parse: true)
      expect(@documents.at(0).get('tagIds')).to.deep.eq({ 1: null, 2: null, 3: null })

    it 'should parse page_number to pageNumber', ->
      @documents.add([ id: 1, page_number: 2 ], parse: true)
      expect(@documents.at(0).get('pageNumber')).to.eq(2)

    it 'should parse a null pageNumber by default', ->
      @documents.add([ id: 1 ], parse: true)
      expect(@documents.at(0).get('pageNumber')).to.be.null

    it 'should tag the collection', ->
      tag = { cid: '123' }
      @documents.tag(tag)
      expect(@documents.hasTag(tag)).to.be.true

    it 'should trigger tag', ->
      tag = { cid: '123' }
      @documents.on('tag', spy = sinon.spy())
      @documents.tag(tag)
      expect(spy).to.have.been.calledWith(tag)

    it 'should unsetTag a document when tagging a collection', ->
      tag = { cid: '123' }
      @documents.add(title: 'title')
      @documents.at(0).tag(tag)
      @documents.tag(tag)
      expect(@documents.at(0).attributes.tagCids[tag.cid]).to.be.null

    it 'should untag the collection', ->
      tag = { cid: '123' }
      @documents.untag(tag)
      expect(@documents.hasTag(tag)).to.be.false

    it 'should trigger untag', ->
      tag = { cid: '123' }
      @documents.on('untag', spy = sinon.spy())
      @documents.untag(tag)
      expect(spy).to.have.been.calledWith(tag)

    it 'should tag nothing by default', ->
      tag = { cid: '123' }
      expect(@documents.hasTag(tag)).to.be.undefined
