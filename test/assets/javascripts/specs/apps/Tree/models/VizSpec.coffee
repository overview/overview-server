define [ 'apps/Tree/models/Viz' ], (Viz) ->
  describe 'apps/Tree/models/Viz', ->
    it 'should have a type', -> expect(new Viz().get('type')).not.to.be.undefined
    it 'should have a title', -> expect(new Viz().get('title')).not.to.be.undefined
    it 'should have creationData', -> expect(new Viz().get('creationData')).to.deep.eq([])
    it 'should make createdAt a date', -> expect(new Viz({ createdAt: '2014-05-27T14:23:01Z' }).get('createdAt')).to.deep.eq(new Date('2014-05-27T14:23:01Z'))
    it 'should have its type in its id', -> expect(new Viz(id: 3, type: 'job').id).to.eq('job-3')
