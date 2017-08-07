define [
  'apps/Show/models/DocumentDisplayPreferences'
], (Model) ->
  describe 'apps/Show/models/DocumentDisplayPreferences', ->
    beforeEach ->
      @prefs = new Model()

    it 'should set and get the sidebar pref', ->
      @prefs.set(sidebar: true)
      expect(new Model().get('sidebar')).to.be.true
      @prefs.set(sidebar: false)
      expect(new Model().get('sidebar')).to.be.false
