define [
  'jquery'
  'backbone'
  'rsvp'
  'i18n'
  'apps/DocumentDisplay/views/TextView'
], ($, Backbone, RSVP, i18n, TextView) ->
  describe 'apps/DocumentDisplay/views/TextView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.Document.show.TextView',
        loading: 'loading'
        error: 'error'

      @preferences = new Backbone.Model(wrap: true)
      @capabilities = new Backbone.Model(canWrap: null)
      @$ = (args...) => @subject.$(args...)
      @model = new Backbone.Model
        text: null
        error: null
        highlights: null
      @$div = $('<div></div>').appendTo('body') # so we can calculate widths
      @subject = new TextView(model: @model, preferences: @preferences, currentCapabilities: @capabilities)
      @subject.$el.appendTo(@$div)

    afterEach ->
      @subject.remove()
      @$div.remove()

    it 'should render a loading message when loading', ->
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render loading when given null text', ->
      @model.set(text: 'foo')
      @model.set(text: null)
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render an error message when loading fails', ->
      @model.set(text: null, error: 'foo')
      expect(@subject.$('.error')).to.contain('error')

    it 'should render text when loading succeeds', ->
      @model.set(text: 'foobar')
      expect(@subject.$('pre')).to.contain('foobar')

    it 'should render highlights when they come after text', ->
      @model.set(text: 'foo bar moo mar')
      @model.set(highlights: [[4,7],[12,15]])
      expect(@subject.$('pre').html()).to.eq('foo <em class="highlight">bar</em> moo <em class="highlight">mar</em>')

    it 'should still say loading if highlights come before text', ->
      @model.set(highlights: [[4,7],[12,15]])
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render highlights when they come before text', ->
      @model.set(highlights: [[4,7],[12,15]])
      @model.set(text: 'foo bar moo mar')
      expect(@subject.$('pre').html()).to.eq('foo <em class="highlight">bar</em> moo <em class="highlight">mar</em>')

    it 'should wrap when preference is set', ->
      @preferences.set(wrap: true)
      @model.set(text: 'foobar')
      expect(@subject.$('pre')).to.have.class('wrap')

    it 'should not wrap when preference is unset', ->
      @preferences.set(wrap: false)
      @model.set(text: 'foobar')
      expect(@subject.$('pre')).not.to.have.class('wrap')

    it 'should disable wrap after rendering if preference is unset', ->
      @preferences.set(wrap: true)
      @model.set(text: 'foobar')
      @preferences.set(wrap: false)
      expect(@subject.$('pre')).not.to.have.class('wrap')

    it 'should enable wrap after rendering if preference is set', ->
      @preferences.set(wrap: false)
      @model.set(text: 'foobar')
      @preferences.set(wrap: true)
      expect(@subject.$('pre')).to.have.class('wrap')

    it 'should set currentCapabilities.canWrap=null when loading', ->
      @model.set(text: 'foo')
      @capabilities.set(canWrap: true)
      @model.set(text: null)
      expect(@capabilities.get('canWrap')).to.be.null

    it 'should set currentCapabilities.canWrap=false when the text is not as wide as its container', ->
      @$div.css(width: '3em')
      @model.set(text: 'foo\nbar\nbaz')
      expect(@capabilities.get('canWrap')).to.be.false

    it 'should set currentCapabilities.canWrap=true when the text is wider than its container', ->
      @$div.css(width: '3em')
      @model.set(text: 'foo bar baz')
      expect(@capabilities.get('canWrap')).to.be.true

    it 'should set currentCapabilities.canWrap=true when the div shrinks because of window resize', ->
      @$div.css(width: '20em')
      @model.set(text: 'foo bar baz')
      @$div.css(width: '3em')
      $(window).trigger('resize')
      expect(@capabilities.get('canWrap')).to.be.true
