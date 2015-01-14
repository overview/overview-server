define [
  'backbone'
  'apps/Show/models/ShowAppFacade'
], (Backbone, ShowAppFacade) ->
  class MockTags extends Backbone.Collection

  describe 'apps/Show/models/ShowAppFacade', ->
    beforeEach ->
      @resetByNodeSpy = sinon.spy()
      @state =
        resetDocumentListParams: =>
          byNode: @resetByNodeSpy
      @tags = new MockTags([ id: 1 ])
      @subject = new ShowAppFacade
        state: @state
        tags: @tags

    describe 'resetDocumentListParams', ->
      it 'should delegate to State', ->
        arg = { foo: 'bar' }
        @subject.resetDocumentListParams().byNode(arg)
        expect(@resetByNodeSpy).to.have.been.calledWith(arg)

    describe 'getTag', ->
      it 'should find a valid tag by CID', ->
        tag = @tags.at(0)
        expect(@subject.getTag(tag.cid)).to.eq(tag)

      it 'should return undefined on a missing tag', ->
        expect(@subject.getTag(123456)).to.be.undefined
