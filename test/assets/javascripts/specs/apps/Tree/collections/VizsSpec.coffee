define [
  'apps/Tree/collections/Vizs'
], (Vizs) ->
  describe 'apps/Tree/collections/Vizs', ->
    it 'should contain Viz objects', ->
      expect(Vizs.prototype.model.name).to.eq('Viz')

    it 'should order by createdAt descending', ->
      vizs = new Vizs([
        { id: 1, type: 'viz', createdAt: '2014-06-04T12:30:01Z' }
        { id: 2, type: 'viz', createdAt: '2014-06-04T12:31:01Z' }
      ])
      expect(vizs.pluck('id')).to.deep.eq([ 2, 1 ])

    it 'should order jobs before errors before vizs', ->
      vizs = new Vizs([
        { id: 1, type: 'viz', createdAt: '2014-06-04T12:30:01Z' }
        { id: 2, type: 'error' }
        { id: 3, type: 'job' }
      ])
      expect(vizs.pluck('id')).to.deep.eq([ 3, 2, 1 ])
