define [
  'apps/ApiTokens/models/ApiToken'
], (ApiToken) ->
  describe 'apps/ApiTokens/models/ApiToken', ->
    it 'should have isNew=true if it has no token', ->
      expect(new ApiToken(token: null).isNew()).to.be.true

    it 'should have isNew=false if it has a token', ->
      expect(new ApiToken(token: '12345').isNew()).to.be.false

    it 'should parse a date', ->
      d = new Date(1405949893639)
      s = '2014-07-21T13:38:13.639Z'
      expect(new ApiToken({ createdAt: s }, parse: true).get('createdAt')).to.deep.eq(d)
