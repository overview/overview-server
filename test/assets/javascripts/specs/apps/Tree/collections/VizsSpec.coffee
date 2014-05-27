define [
  'apps/Tree/collections/Vizs'
], (Vizs) ->
  describe 'apps/Tree/collections/Vizs', ->
    it 'should contain Viz objects', ->
      expect(Vizs.prototype.model.name).to.eq('Viz')
