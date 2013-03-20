require [
  'apps/DocumentDisplay/models/Preferences'
], (Preferences) ->
  describe 'apps/DocumentDisplay/models/Preferences', ->
    PREF = 'sidebar' # example pref
    prefs = undefined

    beforeEach ->
      prefs = new Preferences()

    it 'should set and get the sidebar pref', ->
      prefs.setPreference(PREF, true)
      expect(prefs.getPreference(PREF)).toBe(true)
      prefs.setPreference(PREF, false)
      expect(prefs.getPreference(PREF)).toBe(false)

    it 'should trigger change on change', ->
      called = false
      prefs.setPreference(PREF, true)
      prefs.on('change', -> called = true)
      prefs.setPreference(PREF, false)
      expect(called).toBe(true)

    it 'should not trigger change on not-change', ->
      called = false
      prefs.setPreference(PREF, true)
      prefs.on('change', -> called = true)
      prefs.setPreference(PREF, true)
      expect(called).toBe(false)
