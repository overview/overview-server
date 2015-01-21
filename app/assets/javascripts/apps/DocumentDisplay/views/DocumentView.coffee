define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.Document.show.DocumentView')
  TWITTER_WIDGETS_URL = '//platform.twitter.com/widgets.js'

  class DocumentView extends Backbone.View
    className: 'document'

    initialize: (options) ->
      throw 'Must pass options.preferences, a Preferences' if !options.preferences
      @preferences = options.preferences
      @setUrlProperties(null)

      @listenTo(@preferences, 'change:sidebar', @render)

    setUrlProperties: (@urlProperties) ->
      @render()

    templates:
      documentCloud: _.template("""
        <iframe src="<%- url.url + '?sidebar=' + (preferences.get('sidebar') && 'true' || 'false') + (url.page || '') %>"></iframe>
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
        <object data="<%- url.url + '#scrollbar=1&toolbar=1&navpanes=1&view=FitH' %>" type="application/pdf" width="100%" height="100%">
          <div class='missing-plugin'>
            <a href="<%- url.url %>"><i class="icon-cloud-download"></i></a>
            <%= t('missingPlugin') %>
          </div>
        </object>
      """)

    render: ->
      return @$el.empty() if !@urlProperties

      type = @urlProperties.type

      html = @templates[type]?(t: t, preferences: @preferences, url: @urlProperties) || ''
      @$el.html(html)

      @_renderTweet() if type == 'twitter'

      @

    _renderTweet: ->
      # Twitter's API is undocumented. It goes like this:
      #
      # * On load, twttr.widgets.load() is called (and can't be stopped?).
      #   It replaces any blockquote.twitter-tweet with a tweet, using the
      #   URL in the <a> tag. If the tweet can't be loaded, the blockquote
      #   stays. When the tweet loads, the blockquote is removed.
      #
      # * After load, twttr.widgets.load() can't be called again. But we can
      #   call twttr.widgets.createTweetEmbed(). This accepts an ID, a DOM
      #   element, and an on-finished callback. It appends an iframe to the
      #   DOM element then calls the callback. No iframe, no callback.
      #
      # There's no error-handling.
      if !@twttrLoad?
        # First load. Twitter will automatically find the blockquote and
        # apply itself to it.
        @twttrLoad = $.getScript(TWITTER_WIDGETS_URL)
      else if !twttr?.widgets?.loaded
        # Twitter still hasn't loaded; it will soon, and when it does it will
        # find and change the blockquote
      else
        # Twitter is loaded
        blockquote = @$('blockquote')[0]
        id = blockquote.getAttribute('data-tweet-id')

        twttr.widgets.createTweetEmbed id, blockquote.parentNode, ->
          $(blockquote).remove()

      # Sometimes Twitter doesn't load or doesn't return a tweet--if it's
      # private or if it was deleted.
      #
      # Show the block quote after a delay. This will only be noticeable when
      # the blockquote is still in the DOM -- meaning Twitter isn't feeding us
      # the tweet.
      #
      # From a 1hr search, it seems impossible to detect a failure from
      # Twitter's JavaScript--and the iframe-insertion happens asynchronously.
      # So there's nothing better than this.
      #
      # If the embed succeeds, the blockquote will not be on the page.
      window.setTimeout((-> $(blockquote).show()), 1000)
