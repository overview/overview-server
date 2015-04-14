define [ 'underscore', 'apps/Show/collections/Tags' ], (_, Tags) ->
  describe 'apps/Show/collections/Tags', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @subject = new Tags([], url: '/documentsets/4/tags')

    afterEach ->
      @sandbox.restore()

    it 'should fetch from the correct url', ->
      @subject.fetch()
      req = @sandbox.server.requests[0]
      expect(req).not.to.be.undefined
      expect(req.url).to.eq('/documentsets/4/tags')

    it 'should sort by name', ->
      @subject.add({ name: 'tag2' })
      @subject.add({ name: 'tag1' })
      expect(@subject.pluck('name')).to.deep.eq([ 'tag1', 'tag2' ])

    it 'should give each Tag the correct URL', ->
      @subject.add(name: 'foo', id: 1)
      expect(_.result(@subject.get(1), 'url')).to.eq('/documentsets/4/tags/1')
