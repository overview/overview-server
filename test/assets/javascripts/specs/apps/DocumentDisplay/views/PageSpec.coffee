define [
  'jquery'
  'backbone'
  'i18n'
  'apps/DocumentDisplay/views/Page'
], ($, Backbone, i18n, Page) ->
  describe 'apps/DocumentDisplay/views/Page', ->
    textPromise =
      then: (cb) -> cb('text')

    preferences = undefined
    state = undefined
    view = undefined

    Preferences = Backbone.Model.extend
      defaults: { sidebar: false, wrap: true }
      getPreference: (args...) -> @get.apply(this, args)
      setPreference: (args...) -> @set.apply(this, args)

    beforeEach ->
      i18n.reset_messages
        'views.Document.show.source': 'source'
        'views.Document.show.iframe.enable': 'enable-iframe'
        'views.Document.show.iframe.disable': 'disable-iframe'
        'views.Document.show.sidebar.enable': 'enable-sidebar'
        'views.Document.show.sidebar.disable': 'disable-sidebar'
        'views.Document.show.wrap.enable': 'enable-wrap'
        'views.Document.show.wrap.disable': 'disable-wrap'
        'views.Document.show.buttons.document': 'document-button'
        'views.Document.show.buttons.text': 'text-button'
        'views.Document.show.missing_plugin': 'missing-plugin'
      preferences = new Preferences()
      state = new Backbone.Model(preferences: preferences)
      view = new Page(model: state)
      @sandbox = sinon.sandbox.create()

    afterEach ->
      @sandbox.restore()

    it 'should not render anything when there is no document', ->
      view.render()
      expect(view.$el.html()).to.eq('')

    it 'should render when the document changes', ->
      view.render = sinon.spy()
      state.set('document', {})
      expect(view.render).to.have.been.called

    it 'should render when the preferences change', ->
      view.render = sinon.spy()
      preferences.trigger('change')
      expect(view.render).to.have.been.called

    describe 'with a DocumentCloud document', ->
      document = undefined

      beforeEach ->
        document =
          urlProperties:
            type: 'documentCloud'
            url: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal'
        state.set('document', document)

      it 'should render an iframe', ->
        $iframe = view.$('iframe')
        expect($iframe.length).to.eq(1)

      it 'should have an enable-sidebar link', ->
        $a = view.$('a.boolean-preference[data-preference=sidebar][data-enabled=false]')
        expect($a.length).to.eq(1)

      it 'should change pref when clicking the enable-sidebar link', ->
        preferences.set = sinon.spy()
        $a = view.$('a.boolean-preference[data-preference=sidebar][data-enabled=false]')
        $a.click()
        expect(preferences.set).to.have.been.calledWith('sidebar', true)

      it 'should have a disable-sidebar link', ->
        preferences.set('sidebar', true)
        view.render()
        $a = view.$('a.boolean-preference[data-preference=sidebar][data-enabled=true]')
        expect($a.length).to.eq(1)

      it 'should change pref when clicking the disable-sidebar link', ->
        preferences.set('sidebar', true)
        preferences.set = sinon.spy()
        view.render()
        $a = view.$('a.boolean-preference[data-preference=sidebar][data-enabled=true]')
        $a.click()
        expect(preferences.set).to.have.been.calledWith('sidebar', false)

      it 'should have sidebar=true in the URL when the pref is true', ->
        preferences.set('sidebar', true)
        url = view.$('iframe').attr('src')
        expect(url).to.match(/sidebar=true/)

      it 'should have sidebar=false in the URL when the pref is false', ->
        preferences.set('sidebar', false)
        url = view.$('iframe').attr('src')
        expect(url).to.match(/sidebar=false/)

    describe 'with a tweet', ->
      document = undefined
      twttrDeferred = undefined

      beforeEach ->
        window.twttr = undefined
        document =
          getText: -> textPromise
          urlProperties:
            type: 'twitter'
            username: 'username'
            id: 124512312
            url: '//twitter.com'
        twttrDeferred = new $.Deferred()
        @sandbox.stub($, 'getScript').returns(twttrDeferred)
        state.set('document', document)

      it 'should render a blockquote', ->
        expect(view.$('blockquote')).to.contain('text')

      it 'should load a Twitter script', ->
        expect($.getScript).to.have.been.called

      it 'should not call twttr.widgets.createTweetEmbed with the first tweet', ->
        window.twttr = { widgets: { createTweetEmbed: sinon.spy() } }
        twttrDeferred.resolve()
        expect(window.twttr.widgets.createTweetEmbed).not.to.have.been.called

      it 'should not call twttr.widgets.createTweetEmbed if Twitter is not loaded', ->
        # This is a race:
        # 1) Twitter finishes loading
        # 2) user clicks new tweet
        # 3) Twitter's "ready" code runs
        window.twttr = { widgets: { loaded: false, createTweetEmbed: sinon.spy() } }
        twttrDeferred.resolve()
        expect(window.twttr.widgets.createTweetEmbed).not.to.have.been.called

      it 'should call twttr.widgets.createTweetEmbed if twttr.widgets.loaded', ->
        window.twttr = { widgets: { loaded: true, createTweetEmbed: sinon.spy() } }
        twttrDeferred.resolve()
        state.set(document: {
          getText: -> textPromise
          urlProperties:
            type: 'twitter'
            username: 'username'
            id: 124512313
            url: '//twitter.com'
        })
        expect(window.twttr.widgets.createTweetEmbed).to.have.been.called

    describe 'with a secure document', ->
      document = undefined

      beforeEach ->
        document =
          getText: -> textPromise
          urlProperties:
            type: 'secure'
            url: 'https://example.org'
        state.set(document: document)

      it 'should show an enable-iframe link', ->
        expect(view.$('a.boolean-preference[data-preference=iframe]').length).to.eq(1)

      it 'should render an iframe', ->
        preferences.setPreference('iframe', true)
        $iframe = view.$('iframe')
        expect($iframe.length).to.eq(1)

      it 'should not show the wrap option if showing an iframe', ->
        preferences.setPreference('iframe', true)
        expect(view.$('a.boolean-preference[data-preference=wrap]').length).to.eq(0)

      it 'should show the wrap option if not showing an iframe', ->
        preferences.setPreference('iframe', false)
        expect(view.$('a.boolean-preference[data-preference=wrap]').length).to.eq(1)

    describe 'with a Facebook object', ->
      beforeEach ->
        document =
          getText: -> textPromise
          urlProperties:
            type: 'facebook'
            url: '//www.facebook.com/adam.hooper/posts/10101122388042297'
        state.set('document', document)

      it 'should link to the source', ->
        $a = view.$('a[href="//www.facebook.com/adam.hooper/posts/10101122388042297"]')
        expect($a.length).to.eq(1)

      it 'should render a <pre> that wraps', ->
        expect(view.$('pre.wrap').length).to.eq(1)

      it 'should render content in the <pre>', ->
        expect(view.$('pre')).to.contain('text')

      it 'should not have any preferences', ->
        expect(view.$('a.boolean-preference').length).to.eq(0)

    describe 'with a locally-stored PDF document', ->
      beforeEach ->
        document =
          urlProperties:
            type: 'localObject'
            url: '/documents/1234/contents/4567'
        state.set(document: document)

      it 'creates an object pointing to the document, with pdf viewer settings', ->
        expect(view.$('object')).to.have.attr('data', '/documents/1234/contents/4567#scrollbar=1&toolbar=1&navpanes=1&view=FitH')

      it 'shows the missing plugin message', ->
        expect(view.$('object')).to.contain('missing-plugin')

      it 'does not have any preferences', ->
        expect(view.$('page')).not.to.have('a.boolean-preference')

    describe 'with an insecure document', ->
      beforeEach ->
        document =
          getText: -> textPromise
          urlProperties:
            type: 'insecure'
            url: 'http://example.org'
        state.set(document: document)

      it 'should link to the source', ->
        $a = view.$('a[href="http://example.org"]')
        expect($a.length).to.eq(1)

      it 'should render a <pre>', ->
        expect(view.$('pre')).to.contain('text')

      it 'should have an enable-wrap link', ->
        preferences.set('wrap', false)
        expect(view.$('a.boolean-preference[data-preference=wrap]').length).to.eq(1)

    describe 'with an unknown URL', ->
      beforeEach ->
        document =
          getText: -> textPromise
          urlProperties:
            type: 'unknown'
            url: 'abc123'
        state.set(document: document)

      it 'should render a <pre>', ->
        expect(view.$('pre')).to.contain('text')

      it 'should have an enable-wrap link', ->
        preferences.set('wrap', false)
        expect(view.$('a.boolean-preference[data-preference=wrap]').length).to.eq(1)

    describe 'with no URL', ->
      beforeEach ->
        document =
          getText: -> textPromise
          urlProperties:
            type: 'none'
            url: ''
        state.set(document: document)

      it 'should render a <pre>', ->
        expect(view.$('pre')).to.contain('text')

      it 'should have an enable-wrap link', ->
        preferences.set('wrap', false)
        expect(view.$('a.boolean-preference[data-preference=wrap]').length).to.eq(1)
