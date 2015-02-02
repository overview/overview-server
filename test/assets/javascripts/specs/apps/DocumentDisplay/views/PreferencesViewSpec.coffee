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
        canShowSidebar: null
        canWrap: null

    beforeEach ->
      i18n.reset_messages_namespaced 'views.Document.show.PreferencesView',
        'showing.document': 'showing.document'
        'showing.text': 'showing.text'
        'expand': 'expand'
        'showing.expanded': 'showing.expanded'
        'collapse.top': 'collapse.top'
        'collapse.bottom': 'collapse.bottom'
        'text.false': 'text.false'
        'text.true': 'text.true'
        'text.disabled': 'text.disabled'
        'options': 'options'
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
        @capabilities.set(canShowDocument: true, canShowSidebar: false, canWrap: true)
        @init()

      it 'should expand when clicking expand', ->
        @$('a.expand').click()
        expect(@subject.$el).to.have.class('expanded')

      it 'should collapse when clicking top collapse button', ->
        @$('.expand').click()
        @$('.collapse:eq(0)').click()
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should collapse when clicking bottom collapse button', ->
        @$('.expand').click()
        @$('.collapse:eq(1)').click()
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should collapse when calling hide()', ->
        @$('.expand').click()
        @subject.hide()
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should set the current preferences', ->
        expect(@$('[name=text][value=false]')).to.be.checked
        expect(@$('[name=wrap]')).to.be.checked
        expect(@$('[name=sidebar]')).not.to.be.checked

      it 'should set and unset text', ->
        @$('[name=text][value=true]').prop('checked', true).change()
        @$('[name=text][value=false]').prop('checked', false).change()
        expect(@preferences.get('text')).to.be.true
        @$('[name=text][value=true]').prop('checked', false).change()
        @$('[name=text][value=false]').prop('checked', true).change()
        expect(@preferences.get('text')).to.be.false

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

      it 'should say when showing text (canShowDocument=false)', ->
        @preferences.set(text: false)
        @capabilities.set(canShowDocument: false)
        expect(@$('span.showing')).to.have.text('showing.text')

      it 'should say when showing text (text=true)', ->
        @preferences.set(text: true)
        expect(@$('span.showing')).to.have.text('showing.text')

      it 'should say when showing document', ->
        @preferences.set(text: false)
        @capabilities.set(canShowDocument: true)
        expect(@$('span.showing')).to.have.text('showing.document')

      it 'should add capabilities as classes', ->
        @capabilities.set(canShowDocument: true, canShowSidebar: true, canWrap: true)
        expect(@subject.$el).to.have.class('can-show-document')
        expect(@subject.$el).to.have.class('can-show-sidebar')
        expect(@subject.$el).to.have.class('can-wrap')
        @capabilities.set(canShowDocument: false, canShowSidebar: false, canWrap: false)
        expect(@subject.$el).not.to.have.class('can-show-document')
        expect(@subject.$el).not.to.have.class('can-show-sidebar')
        expect(@subject.$el).not.to.have.class('can-wrap')
        @capabilities.set(canShowDocument: null, canShowSidebar: null, canWrap: null)
        expect(@subject.$el).not.to.have.class('can-show-document')
        expect(@subject.$el).not.to.have.class('can-show-sidebar')
        expect(@subject.$el).not.to.have.class('can-wrap')

      it 'should disable the showing fieldset when canShowDocument=false', ->
        @capabilities.set(canShowDocument: true)
        expect(@$('fieldset.showing')).to.be.enabled
        @capabilities.set(canShowDocument: false)
        expect(@$('fieldset.showing')).to.be.disabled
        @capabilities.set(canShowDocument: null)
        expect(@$('fieldset.showing')).to.be.disabled
