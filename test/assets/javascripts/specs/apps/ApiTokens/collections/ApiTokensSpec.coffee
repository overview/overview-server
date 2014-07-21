define [
  'apps/ApiTokens/collections/ApiTokens'
], (ApiTokens) ->
  describe 'apps/ApiTokens/collections/ApiTokens', ->
    beforeEach ->
      @subject = new ApiTokens([{}], documentSetId: 10)

    it 'should have the correct url', ->
      expect(@subject.url()).to.eq('/documentsets/10/api-tokens')

    it 'should give its members the correct URLs', ->
      # Not quite a unit test, but ApiToken on its own doesn't know its URL
      @subject = new ApiTokens([{ token: '12345' }], documentSetId: 10)
      expect(@subject.at(0).url()).to.eq('/documentsets/10/api-tokens/12345')
