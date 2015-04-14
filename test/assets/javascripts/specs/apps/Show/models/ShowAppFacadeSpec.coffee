define [
  'backbone'
  'apps/Show/models/ShowAppFacade'
], (Backbone, ShowAppFacade) ->
  class MockTags extends Backbone.Collection

  describe 'apps/Show/models/ShowAppFacade', ->
    beforeEach ->
      @resetByNodeSpy = sinon.spy()
      @state = { setDocumentListParams: sinon.spy() }
      @tags = new MockTags([ id: 1 ])
      @subject = new ShowAppFacade
        state: @state
        tags: @tags

    describe 'setDocumentListParams', ->
      it 'should delegate to State', ->
        arg = { foo: 'bar' }
        @subject.setDocumentListParams(arg)
        expect(@state.setDocumentListParams).to.have.been.calledWith(arg)

    describe 'getTag', ->
      it 'should find a valid tag by CID', ->
        tag = @tags.at(0)
        expect(@subject.getTag(tag.cid)).to.eq(tag)

      it 'should return undefined on a missing tag', ->
        expect(@subject.getTag(123456)).to.be.undefined
