define [
  'jquery'
  'backbone'
  'i18n'
  'apps/DocumentDisplay/views/PreferencesView'
], ($, Backbone, i18n, PreferencesView) ->
  describe 'apps/DocumentDisplay/views/PreferencesView', ->
    class Preferences extends Backbone.Model
      defaults:
        text: false
        sidebar: false
        wrap: true

    class CurrentCapabilities extends Backbone.Model
      defaults:
        canShowDocument: null

    beforeEach ->
      i18n.reset_messages_namespaced 'views.Document.show.PreferencesView',
        'text.false': 'text.false'
        'text.true': 'text.true'
        'sidebar': 'sidebar'
        'wrap': 'wrap'

      @preferences = new Preferences
      @capabilities = new CurrentCapabilities
      @$div = $('<div></div>').appendTo('.body')
      @$ = (args...) => @subject.$(args...)
      @init = =>
        @subject = new PreferencesView(preferences: @preferences, currentCapabilities: @capabilities)
        @$div.append(@subject.$el)

    afterEach ->
      @$div.remove()

    describe 'starting with typical capabilities', ->
      beforeEach ->
        @preferences.set(text: false, sidebar: false, wrap: true)
        @capabilities.set(canShowDocument: true)
        @init()

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
