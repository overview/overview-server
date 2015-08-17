define [
  'jquery'
  'backbone'
  'i18n'
  'apps/Show/views/DocumentDisplayPreferencesView'
], ($, Backbone, i18n, PreferencesView) ->
  describe 'apps/Show/views/DocumentDisplayPreferencesView', ->
    class Preferences extends Backbone.Model
      defaults:
        text: false
        sidebar: false
        wrap: true

    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentDisplayPreferences',
        'text.false': 'text.false'
        'text.true': 'text.true'
        'sidebar': 'sidebar'
        'wrap': 'wrap'
        'openInNewTab': 'openInNewTab'

      @preferences = new Preferences
      @$ = (args...) => @subject.$(args...)
      @subject = new PreferencesView(model: @preferences)

    afterEach ->
      @subject.remove()

    it 'should set the current preferences', ->
      expect(@$('[name=text]')).not.to.be.checked
      expect(@$('[name=wrap]')).to.be.checked
      expect(@$('[name=sidebar]')).not.to.be.checked

    it 'should set and unset text', ->
      @$('.text-on').click()
      expect(@preferences.get('text')).to.be.true
      expect(@$('[name=text]')).to.be.checked
      @$('.text-off').click()
      expect(@preferences.get('text')).to.be.false
      expect(@$('[name=text]')).not.to.be.checked

    it 'should set and unset sidebar', ->
      @$('[name=sidebar]').prop('checked', true).change()
      expect(@preferences.get('sidebar')).to.be.true
      @$('[name=sidebar]').prop('checked', false).change()
      expect(@preferences.get('sidebar')).to.be.false

    it 'should set and unset wrap', ->
      @$('[name=wrap]').prop('checked', false).change()
      expect(@preferences.get('wrap')).to.be.false
      @$('[name=wrap]').prop('checked', true).change()
      expect(@preferences.get('wrap')).to.be.true

    it 'should update openInNewTab href when documentUrl changes', ->
      @preferences.set('documentUrl': 'foobar')
      expect(@$('.open-in-new-tab').attr('href')).to.eq('foobar')
