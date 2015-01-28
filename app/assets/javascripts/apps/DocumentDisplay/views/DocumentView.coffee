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
      # Twitter's JS docs are here: https://dev.twitter.com/web/javascript/events
      #
      # 1. First tweet: we include the script. (This is complicated just for steps 2+3)
      # 2. Another tweet, before the script is loaded: we do nothing
      # 3. Another tweet, after the script is loaded: we call createTweet
      @twttrState ||= "unloaded"

      switch @twttrState
        when "unloaded"
          @twttrState = "loading"
          window.twttr =
            _e: [ => twttr.events.bind('loaded', => @twttrState = "loaded") ]
          $.getScript(TWITTER_WIDGETS_URL)
        when "loading" then # do nothing
        when "loaded"
          # Twitter is loaded
          blockquote = @$('blockquote')[0]
          id = blockquote.getAttribute('data-tweet-id')
          twttr.widgets.createTweet(id, blockquote.parentNode)
            .then -> $(blockquote).remove()
            .catch -> $(blockquote).show()
