define [
  'apps/DocumentDisplay/models/Preferences'
], (Preferences) ->
  describe 'apps/DocumentDisplay/models/Preferences', ->
    prefs = undefined

    beforeEach ->
      prefs = new Preferences()

    it 'should set and get the sidebar pref', ->
      prefs.set(sidebar: true)
      expect(new Preferences().get('sidebar')).to.be.true
      prefs.set(sidebar: false)
      expect(new Preferences().get('sidebar')).to.be.false

    it 'should trigger change on change', ->
      called = false
      prefs.set(sidebar: true)
      prefs.on('change', -> called = true)
      prefs.set(sidebar: false)
      expect(called).to.be.true

    it 'should not trigger change on not-change', ->
      called = false
      prefs.set(sidebar: true)
      prefs.on('change', -> called = true)
      prefs.set(sidebar: true)
      expect(called).to.be.false
