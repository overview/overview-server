define [
  'jquery'
  'backbone'
  'i18n'
  'apps/DocumentDisplay/views/DocumentView'
], ($, Backbone, i18n, DocumentView) ->
  describe 'apps/DocumentDisplay/views/DocumentView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.Document.show.DocumentView',
        'twitter.loading.text': 'twitter.loading.text'
        'twitter.loading.url': 'twitter.loading.url'
        'missingPlugin': 'missingPlugin'

      @sandbox = sinon.sandbox.create()
      @$ = (args...) => @subject.$(args...)
      @preferences = new Backbone.Model(sidebar: false, wrap: true, text: false)
      @init = (urlProperties) =>
        @subject = new DocumentView(preferences: @preferences)
        @subject.setUrlProperties(urlProperties)

    afterEach ->
      @sandbox.restore()

    it 'should render null as nothing', ->
      @init(null)
      expect(@subject.$el).to.be.empty

    describe 'with a DocumentCloud document', ->
      beforeEach ->
        @init
          type: 'documentCloud'
          url: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal'

      it 'should render an iframe', ->
        expect(@$('iframe')).to.have.attr('src', 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal?sidebar=false')

      it 'should add and remove a sidebar', ->
        @preferences.set(sidebar: true)
        expect(@$('iframe')).to.have.attr('src', 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal?sidebar=true')
        @preferences.set(sidebar: false)
        expect(@$('iframe')).to.have.attr('src', 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal?sidebar=false')

      it 'should link to a page', ->
        @subject.setUrlProperties
          type: 'documentCloud'
          url: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal'
          page: '#p2'
        expect(@$('iframe')).to.have.attr('src', 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal?sidebar=false#p2')

    describe 'with a tweet', ->
      beforeEach ->
        @twttrDeferred = new $.Deferred()
        @sandbox.stub($, 'getScript').returns(@twttrDeferred)
        @init
          type: 'twitter'
          username: 'username'
          id: 1234512321
          url: '//twitter.com'

      afterEach ->
        delete window.twttr

      it 'should render a blockquote', ->
        expect(@$('blockquote')).to.have.attr('data-tweet-id', '1234512321')

      it 'should load Twitter', ->
        expect($.getScript).to.have.been.called

      it 'should not call twttr.widgets.createTweetEmbed with the first tweet', ->
        window.twttr = { widgets: { loaded: true, createTweetEmbed: sinon.spy() } }
        @twttrDeferred.resolve()
        expect(window.twttr.widgets.createTweetEmbed).not.to.have.been.called

      it 'should not call twttr.widgets.createTweetEmbed if Twitter is not loaded', ->
        # This is a race:
        # 1) Twitter finishes loading
        # 2) user clicks new tweet
        # 3) Twitter's "ready" code runs
        window.twttr = { widgets: { loaded: false, createTweetEmbed: sinon.spy() } }
        @twttrDeferred.resolve()
        expect(window.twttr.widgets.createTweetEmbed).not.to.have.been.called

      it 'should call twttr.widgets.createTweetEmbed if twttr.widgets.loaded', ->
        window.twttr = { widgets: { loaded: true, createTweetEmbed: sinon.spy() } }
        @twttrDeferred.resolve()
        @subject.setUrlProperties(type: 'twitter', username: 'username', id: 1234512322, url: '//twitter.com')
        expect(window.twttr.widgets.createTweetEmbed).to.have.been.called

    describe 'with an https document', ->
      beforeEach ->
        @init
          type: 'https'
          url: 'https://example.org'

      it 'should render an iframe', ->
        expect(@$('iframe')).to.have.attr('src', 'https://example.org')

    describe 'with an Overview-provided PDF', ->
      beforeEach ->
        @init
          type: 'pdf'
          url: '/documents/1234.pdf'

      it 'should render an object', ->
        $object = @$('object')
        expect($object).to.exist
        expect($object).to.have.attr('data', '/documents/1234.pdf#scrollbar=1&toolbar=1&navpanes=1&view=FitH')
        expect($object).to.contain('missingPlugin')
