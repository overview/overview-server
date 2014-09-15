define [ 'apps/Show/models/Viz' ], (Viz) ->
  describe 'apps/Show/models/Viz', ->
    it 'should have a type', -> expect(new Viz().get('type')).not.to.be.undefined
    it 'should have a title', -> expect(new Viz().get('title')).not.to.be.undefined
    it 'should have creationData', -> expect(new Viz().get('creationData')).to.deep.eq([])
    it 'should make createdAt a date', -> expect(new Viz({ createdAt: '2014-05-27T14:23:01Z' }).get('createdAt')).to.deep.eq(new Date('2014-05-27T14:23:01Z'))
    it 'should have its type in its id', -> expect(new Viz(id: 3, type: 'job').id).to.eq('job-3')
    it 'should have a default type in its id', -> expect(new Viz(id: 3).id).not.to.eq('undefined-3')

    it 'should parse the rootNodeId', ->
      viz = new Viz({ id: 3, creationData: [ [ 'foo', 'bar' ], [ 'rootNodeId', '123456' ] ] }, parse: true)
      expect(viz.get('rootNodeId')).to.eq(123456)

    it 'should scope a non-Node apiParams', ->
      viz = new Viz(id: 3, rootNodeId: 123)
      params = { tags: '3' }
      scoped = viz.scopeApiParams(params)
      expect(params).to.deep.eq(tags: '3')
      expect(scoped).to.deep.eq(tags: '3', nodes: '123')

    it 'should not scope a Node apiParams', ->
      viz = new Viz(id: 3, rootNodeId: 123)
      params = { nodes: '3' }
      scoped = viz.scopeApiParams(params)
      expect(scoped).to.deep.eq(nodes: '3')
