require [
  'jquery'
  'backbone'
  'i18n'
  'apps/DocumentDisplay/views/Page'
], ($, Backbone, i18n, Page) ->
  describe 'apps/DocumentDisplay/views/Page', ->
    preferences = undefined
    state = undefined
    view = undefined

    Preferences = Backbone.Model.extend
      defaults: { sidebar: false, wrap: true }
      getPreference: (args...) -> @get.apply(this, args)
      setPreference: (args...) -> @set.apply(this, args)

    beforeEach ->
      i18n.reset_messages({
        'views.Document.show.source': 'source'
        'views.Document.show.sidebar.enable': 'enable-sidebar'
        'views.Document.show.sidebar.disable': 'disable-sidebar'
        'views.Document.show.wrap.enable': 'enable-wrap'
        'views.Document.show.wrap.disable': 'disable-wrap'
      })
      preferences = new Preferences()
      state = new Backbone.Model({ preferences: preferences })
      view = new Page({ model: state })

    it 'should not render anything when there is no document', ->
      view.render()
      expect(view.$el.html()).toEqual('')

    it 'should render when the document changes', ->
      spyOn(view, 'render')
      state.set('document', new Backbone.Model())
      expect(view.render).toHaveBeenCalled()

    it 'should render when the preferences change', ->
      spyOn(view, 'render')
      preferences.trigger('change')
      expect(view.render).toHaveBeenCalled()

    describe 'with a DocumentCloud document', ->
      document = undefined

      beforeEach ->
        document = new Backbone.Model({
          type: 'DocumentCloudDocument'
        })
        state.set('document', document)

      it 'should render an iframe', ->
        $iframe = view.$('iframe')
        expect($iframe.length).toEqual(1)

      it 'should have an enable-sidebar link', ->
        $a = view.$('a.enable-sidebar')
        expect($a.length).toEqual(1)

      it 'should change pref when clicking the enable-sidebar link', ->
        spyOn(preferences, 'set')
        view.$('a.enable-sidebar').click()
        expect(preferences.set).toHaveBeenCalledWith('sidebar', true)

      it 'should have a disable-sidebar link', ->
        preferences.set('sidebar', true)
        view.render()
        $a = view.$('a.disable-sidebar')
        expect($a.length).toEqual(1)

      it 'should change pref when clicking the disable-sidebar link', ->
        preferences.set('sidebar', true)
        spyOn(preferences, 'set')
        view.render()
        view.$('a.disable-sidebar').click()
        expect(preferences.set).toHaveBeenCalledWith('sidebar', false)

      it 'should have sidebar=true in the URL when the pref is true', ->
        preferences.set('sidebar', true)
        url = view.$('iframe').attr('src')
        expect(url).toMatch(/sidebar=true/)

      it 'should have sidebar=false in the URL when the pref is false', ->
        preferences.set('sidebar', false)
        url = view.$('iframe').attr('src')
        expect(url).toMatch(/sidebar=false/)

    describe 'with a tweet', ->
      document = undefined
      twttrDeferred = undefined

      beforeEach ->
        window.twttr = undefined
        document = new Backbone.Model({
          type: 'TwitterTweet'
          twitterTweet:
            text: 'text'
            username: 'username'
            url: 'url'
        })
        twttrDeferred = new $.Deferred()
        spyOn($, 'getScript').andReturn(twttrDeferred)
        state.set('document', document)

      it 'should render a blockquote', ->
        $blockquote = view.$('blockquote')
        expect($blockquote.length).toEqual(1)

      it 'should load a Twitter script', ->
        expect($.getScript).toHaveBeenCalled()

      it 'should not call twttr.widgets.createTweetEmbed with the first tweet', ->
        window.twttr = { widgets: { createTweetEmbed: -> } }
        spyOn(window.twttr.widgets, 'createTweetEmbed')
        twttrDeferred.resolve()
        expect(window.twttr.widgets.createTweetEmbed).not.toHaveBeenCalled()

      it 'should not call twttr.widgets.createTweetEmbed if Twitter is not loaded', ->
        # This is a race:
        # 1) Twitter finishes loading
        # 2) user clicks new tweet
        # 3) Twitter's "ready" code runs
        window.twttr = { widgets: { loaded: false, createTweetEmbed: -> } }
        spyOn(window.twttr.widgets, 'createTweetEmbed')
        twttrDeferred.resolve()
        expect(window.twttr.widgets.createTweetEmbed).not.toHaveBeenCalled()

      it 'should call twttr.widgets.createTweetEmbed if twttr.widgets.loaded', ->
        window.twttr = { widgets: { loaded: true, createTweetEmbed: -> } }
        spyOn(window.twttr.widgets, 'createTweetEmbed')
        twttrDeferred.resolve()
        state.set 'document', new Backbone.Model
          type: 'TwitterTweet'
          twitterTweet:
            text: 'text2'
            username: 'username2'
            url: 'url2'
        expect(window.twttr.widgets.createTweetEmbed).toHaveBeenCalled()

    describe 'with a secure document', ->
      document = undefined

      beforeEach ->
        document = new Backbone.Model({
          type: 'SecureCsvImportDocument'
          secureSuppliedUrl: 'https://example.org'
        })
        state.set('document', document)

      it 'should render an iframe', ->
        $iframe = view.$('iframe')
        expect($iframe.length).toEqual(1)

    describe 'with a CSV-import document', ->
      document = undefined

      beforeEach ->
        document = new Backbone.Model({
          type: 'CsvImportDocument'
          text: 'text'
        })
        state.set('document', document)

      describe 'without a source URL', ->
        it 'should render a <pre>', ->
          $pre = view.$('pre')
          expect($pre.length).toEqual(1)

        it 'should have an enable-wrap link', ->
          preferences.set('wrap', false)
          $a = view.$('a.enable-wrap')
          expect($a.length).toEqual(1)

        it 'should change pref when clicking the enable-wrap link', ->
          preferences.set('wrap', false)
          spyOn(preferences, 'set')
          view.$('a.enable-wrap').click()
          expect(preferences.set).toHaveBeenCalledWith('wrap', true)

        it 'should have a disable-wrap link', ->
          preferences.set('wrap', true)
          view.render()
          $a = view.$('a.disable-wrap')
          expect($a.length).toEqual(1)

        it 'should change pref when clicking the disable-wrap link', ->
          preferences.set('wrap', true)
          spyOn(preferences, 'set')
          view.render()
          view.$('a.disable-wrap').click()
          expect(preferences.set).toHaveBeenCalledWith('wrap', false)

        it 'should wrap when the pref is on', ->
          preferences.setPreference('wrap', true)
          $pre = view.$('pre')
          expect($pre.hasClass('wrap')).toEqual(true)

        it 'should not wrap when the pref is off', ->
          preferences.setPreference('wrap', false)
          $pre = view.$('pre')
          expect($pre.hasClass('wrap')).toEqual(false)

      describe 'with a source URL', ->
        beforeEach ->
          document.set({
            suppliedUrl: 'http://example.org'
          })
          view.render()

        it 'should link to the source', ->
          $a = view.$('a[href="http://example.org"]')
          expect($a.length).toEqual(1)

        it 'should render a <pre>', ->
          $pre = view.$('pre')
          expect($pre.length).toEqual(1)
