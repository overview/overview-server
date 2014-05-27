define [ 'apps/Tree/models/Viz' ], (Viz) ->
  describe 'apps/Tree/models/Viz', ->
    it 'should have a title', -> expect(new Viz().get('title')).not.to.be.undefined
