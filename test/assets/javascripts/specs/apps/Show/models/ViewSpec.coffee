define [ 'apps/Show/models/View' ], (View) ->
  describe 'apps/Show/models/View', ->
    it 'should have a type', -> expect(new View().get('type')).not.to.be.undefined
    it 'should have a title', -> expect(new View().get('title')).not.to.be.undefined
    it 'should have creationData', -> expect(new View().get('creationData')).to.deep.eq([])
    it 'should make createdAt a date', -> expect(new View({ createdAt: '2014-05-27T14:23:01Z' }).get('createdAt')).to.deep.eq(new Date('2014-05-27T14:23:01Z'))
    it 'should have its type in its id', -> expect(new View(id: 3, type: 'job').id).to.eq('job-3')
    it 'should have a default type in its id', -> expect(new View(id: 3).id).not.to.eq('undefined-3')

    it 'should parse the rootNodeId', ->
      view = new View({ id: 3, creationData: [ [ 'foo', 'bar' ], [ 'rootNodeId', '123456' ] ] }, parse: true)
      expect(view.get('rootNodeId')).to.eq(123456)

    it 'should scope a non-Node apiParams', ->
      view = new View(id: 3, rootNodeId: 123)
      params = { tags: '3' }
      scoped = view.scopeApiParams(params)
      expect(params).to.deep.eq(tags: '3')
      expect(scoped).to.deep.eq(tags: '3', nodes: '123')

    it 'should not scope a Node apiParams', ->
      view = new View(id: 3, rootNodeId: 123)
      params = { nodes: '3' }
      scoped = view.scopeApiParams(params)
      expect(scoped).to.deep.eq(nodes: '3')
