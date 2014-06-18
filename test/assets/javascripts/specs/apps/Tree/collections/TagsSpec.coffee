define [ 'apps/Tree/collections/Tags' ], (Tags) ->
  describe 'apps/Tree/collections/Tags', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @subject = new Tags([], url: '/documentsets/4/tags')

    it 'should fetch from the correct url', ->
      @subject.fetch()
      req = @sandbox.server.requests[0]
      expect(req).not.to.be.undefined
      expect(req.url).to.eq('/documentsets/4/tags')

    it 'should sort by name', ->
      @subject.add({ name: 'tag2' })
      @subject.add({ name: 'tag1' })
      expect(@subject.pluck('name')).to.deep.eq([ 'tag1', 'tag2' ])
