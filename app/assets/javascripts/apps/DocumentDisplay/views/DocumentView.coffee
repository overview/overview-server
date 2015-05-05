define [
  'underscore'
  'jquery'
  'backbone'
  'i18n'
], (_, $, Backbone, i18n) ->
  t = i18n.namespaced('views.Document.show.DocumentView')
  TWITTER_WIDGETS_URL = '//platform.twitter.com/widgets.js'

  TweetCount = 0

  class DocumentView extends Backbone.View
    className: 'document'

    initialize: (options) ->
      throw 'Must pass options.preferences, a Preferences' if !options.preferences
      @preferences = options.preferences
      @setUrlProperties(null)

      @listenTo(@preferences, 'change:sidebar', @render)

    setUrlProperties: (@urlProperties) ->
      @render()

    setUrlPropertiesAndHighlightSearch: (@urlProperties, @highlightSearch) ->
      @render()

    templates:
      documentCloud: _.template("""
        <iframe src="<%- url.url + '?sidebar=' + (preferences.get('sidebar') ? 'true' : 'false') + (url.page || '') %>"></iframe>
      """)

      twitter: _.template("""
        <div class="twitter-tweet-container">
          <blockquote class="twitter-tweet" data-tweet-id="<%- url.id %>">
            <p><%- t('twitter.loading.text') %></p>
            <a href="<%- url.url %>"><%- t('twitter.loading.url') %></a>
          </blockquote>
        </div>
      """)

      https: _.template("""
        <iframe src="<%- url.url %>"></iframe>
      """)

      pdf: _.template("""
        <iframe src="/documents/<%- url.id %><%= preferences.get('sidebar') ? '#pagemode=thumbs' : '' %>"></iframe>
      """)

    render: ->
      return @$el.empty() if !@urlProperties

      type = @urlProperties.type

      html = @templates[type]?(t: t, preferences: @preferences, url: @urlProperties) || ''
      @$el.html(html)
      @$el.attr('data-document-type', type)

      if type == 'twitter'
        @_renderTweet()
      else if type == 'pdf'
        @_renderPdf()

      @

    _renderTweet: ->
      # Twitter's JS docs are here: https://dev.twitter.com/web/javascript/events
      #
      # 1. First tweet: we include the script. (This is complicated just for steps 2+3)
      # 2. Another tweet, before the script is loaded: we do nothing
      # 3. Another tweet, after the script is loaded: we call createTweet

      tweetNumber = (TweetCount += 1)
      blockquote = @$('blockquote')[0]
      id = blockquote.getAttribute('data-tweet-id')
      onCreate = =>
        $(blockquote).remove()
        if tweetNumber == TweetCount && @$('iframe').css('visibility') == 'hidden'
          @trigger('tweet-deleted')

      @twttrState ||= "unloaded"
      switch @twttrState
        when 'unloaded'
          @twttrState = 'loading'
          onLoad = =>
            @twttrState = 'loaded'
            onCreate()

          window.twttr = { _e: [ => twttr.events.bind('loaded', onLoad) ] }
          $.getScript(TWITTER_WIDGETS_URL)
        when 'loading' then # do nothing
        when 'loaded'
          # Twitter is loaded
          twttr.widgets.createTweet(id, blockquote.parentNode)
            .then(onCreate)
            .catch((err) -> console.log('Error from Twitter', error))

    _renderPdf: ->
      # After rendering a PDF, search automatically.
      #
      # Nothing clever here. If you search for `foo OR bar`, the search phrase
      # will be "foo OR bar". We can't get clever here without ripping pdf.js
      # to shreds (or using a whole new viewer strategy).
      if @highlightSearch
        iframe = @$('iframe')[0]
        iWindow = iframe.contentWindow
        iDocument = iframe.contentDocument
        $(iWindow).on 'load', =>
          # Execute the search by firing a 'find' event.
          event = iDocument.createEvent('CustomEvent')
          event.initCustomEvent('find', true, true, {
            query: @highlightSearch
            caseSensitive: false
            highlightAll: true
            findPrevious: false
          })
          iWindow.dispatchEvent(event)

          # Now update and display the Find bar, so users understand what's
          # highlighted.
          findBar = iWindow.PDFViewerApplication.findBar
          findBar.caseSensitive.checked = false
          findBar.highlightAll.checked = true
          findBar.findField.value = @highlightSearch
          findBar.open()
