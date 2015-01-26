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
      @textDeferred = RSVP.defer()
      @highlightsDeferred = RSVP.defer()
      @promise = @textDeferred.promise
      @highlightsPromise = @highlightsDeferred.promise
      @$div = $('<div></div>').appendTo('body')
      @subject = new TextView(preferences: @preferences, currentCapabilities: @capabilities)
      @subject.render()
      @subject.$el.appendTo(@$div)

    afterEach ->
      @textDeferred.reject(null) # free up memory
      @subject.remove()
      @$div.remove()

    it 'should render a loading message when loading', ->
      @subject.setTextPromise(@promise)
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render loading when given null text', ->
      @subject.setTextPromise(null)
      expect(@subject.$('.loading')).to.contain('loading')

    it 'should render an error message when loading fails', ->
      @subject.setTextPromise(@promise)
      @textDeferred.reject(null)
      @promise.then => expect(@subject.$('.error')).to.contain('error')

    it 'should render an error message when loading has already failed', ->
      @textDeferred.reject(null)
      @subject.setTextPromise(@promise)
      @promise.then => expect(@subject.$('.error')).to.contain('error')

    it 'should render text when loading succeeds', ->
      @subject.setTextPromise(@promise)
      @textDeferred.resolve('foobar')
      @promise.then => expect(@subject.$('pre')).to.contain('foobar')

    it 'should render text when text has already loaded', ->
      @textDeferred.resolve('foobar')
      @subject.setTextPromise(@promise)
      @promise.then => expect(@subject.$('pre')).to.contain('foobar')

    it 'should render highlights', ->
      @textDeferred.resolve('foo bar moo mar')
      @highlightsDeferred.resolve([[4, 7], [12, 15]])
      p1 = @subject.setTextPromise(@promise)
      p2 = @subject.setHighlightsPromise(@highlightsPromise)
      RSVP.all([ p1, p2 ])
        .then =>
          expect(@subject.$('pre').html()).to.eq('foo <em class="highlight">bar</em> moo <em class="highlight">mar</em>')

    it 'should render highlights if they load before the text', ->
      @highlightsDeferred.resolve([[0, 3]])
      @subject.setTextPromise(@promise)
      @subject.setHighlightsPromise(@highlightsPromise)
      @highlightsPromise
        .then => @textDeferred.resolve('foo bar')
        .then => expect(@subject.$('pre').html()).to.eq('<em class="highlight">foo</em> bar')

    it 'should wrap when preference is set', ->
      @preferences.set(wrap: true)
      @textDeferred.resolve('foobar')
      @subject.setTextPromise(@promise)
      @promise.then => expect(@subject.$('pre')).to.have.class('wrap')

    it 'should not wrap when preference is unset', ->
      @preferences.set(wrap: false)
      @textDeferred.resolve('foobar')
      @subject.setTextPromise(@promise)
      @promise.then => expect(@subject.$('pre')).not.to.have.class('wrap')

    it 'should disable wrap after rendering if preference is unset', ->
      @preferences.set(wrap: true)
      @textDeferred.resolve('foobar')
      @subject.setTextPromise(@promise)
      @promise
        .then => @preferences.set(wrap: false)
        .then => expect(@subject.$('pre')).not.to.have.class('wrap')

    it 'should enable wrap after rendering if preference is set', ->
      @preferences.set(wrap: false)
      @textDeferred.resolve('foobar')
      @subject.setTextPromise(@promise)
      @promise
        .then => @preferences.set(wrap: true)
        .then => expect(@subject.$('pre')).to.have.class('wrap')

    it 'should set currentCapabilities.canWrap=null when loading', ->
      @subject.setTextPromise(new RSVP.Promise((resolve, reject) -> resolve('foo')))
        .then =>
          @subject.setTextPromise(null)
          expect(@capabilities.get('canWrap')).to.be.null

    it 'should set currentCapabilities.canWrap=false when the text is not as wide as its container', ->
      @$div.css(width: '3em')
      @subject.setTextPromise(@promise)
      @textDeferred.resolve('foo\nbar\nbaz')
      @promise.then => expect(@capabilities.get('canWrap')).to.be.false

    it 'should set currentCapabilities.canWrap=true when the text is wider than its container', ->
      @$div.css(width: '3em')
      @subject.setTextPromise(@promise)
      @textDeferred.resolve('foo bar baz')
      @promise.then => expect(@capabilities.get('canWrap')).to.be.true

    it 'should set currentCapabilities.canWrap=true when the div shrinks because of window resize', ->
      @$div.css(width: '20em')
      @subject.setTextPromise(@promise)
      @textDeferred.resolve('foo bar baz')
      @promise.then =>
        @$div.css(width: '3em')
        $(window).trigger('resize')
        expect(@capabilities.get('canWrap')).to.be.true
