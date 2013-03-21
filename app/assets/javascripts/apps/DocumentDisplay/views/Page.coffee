define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  TWITTER_WIDGETS_URL = '//platform.twitter.com/widgets.js'
  # Scrolling usually leaves a bit of what *was* visible still visible, to
  # help viewers read those last lines. These two variables help decide how
  # many pixels should remain visible after a scroll
  PAGE_KEY_FRACTION = 0.8 # max percentage of an element that should scroll away
  PAGE_KEY_MARGIN = 100 # min px that should remain visible

  t = (key, args...) -> i18n("views.Document.show.#{key}", args...)

  Backbone.View.extend
    className: 'document'

    events:
      'click a.enable-iframe': 'onClickEnableIframe'
      'click a.disable-iframe': 'onClickDisableIframe'
      'click a.enable-sidebar': 'onClickEnableSidebar'
      'click a.disable-sidebar': 'onClickDisableSidebar'
      'click a.enable-wrap': 'onClickEnableWrap'
      'click a.disable-wrap': 'onClickDisableWrap'

    templates:
      DocumentCloudDocument: _.template("""
        <div class="page type-DocumentCloudDocument">
          <ul class="actions">
            <% if (preferences.getPreference('sidebar')) { %>
              <li><a href="#" class="disable-sidebar"><%- t('sidebar.disable') %></a></li>
            <% } else { %>
              <li><a href="#" class="enable-sidebar"><%- t('sidebar.enable') %></a></li>
            <% } %>
          </ul>
          <iframe src="<%- document.get('documentCloudUrl') + '?sidebar=' + (preferences.getPreference('sidebar') && 'true' || 'false') %>"></iframe>
        </div>
      """)

      # TODO Make a helper (the server?) find the tweet ID
      TwitterTweet: _.template("""
        <div class="page type-TwitterTweet">
          <div class="twitter-tweet-container">
            <blockquote class="twitter-tweet" data-tweet-id="<%- _.last(document.get('twitterTweet').url.split('/')) %>">
              <p><%- document.get('twitterTweet').text %></p>
              &mdash;<%- document.get('twitterTweet').username %>
              <a href="<%- document.get('twitterTweet').url %>">...</a>
            </blockquote>
          </div>
        </div>
      """)

      CsvImportDocument: _.template("""
        <div class="page type-CsvImportDocument">
          <ul class="actions">
            <% if (document.get('secureSuppliedUrl')) { %>
              <% if (preferences.getPreference('iframe')) { %>
                <li><a href="#" class="disable-iframe"><%- t('iframe.disable') %></a></li>
              <% } else { %>
                <li><a href="#" class="enable-iframe"><%- t('iframe.enable') %></a></li>
              <% } %>
            <% } %>
            <% if (!document.get('secureSuppliedUrl') || !preferences.getPreference('iframe')) { %>
              <% if (preferences.getPreference('wrap')) { %>
                <li><a href="#" class="disable-wrap"><%- t('wrap.disable') %></a></li>
              <% } else { %>
                <li><a href="#" class="enable-wrap"><%- t('wrap.enable') %></a></li>
              <% } %>
            <% } %>
          </ul>
          <% if (document.get('suppliedUrl')) { %>
            <p class="source">
              <span class="label"><%- t('source') %></span>
              <a href="<%- document.get('suppliedUrl') %>" target="_blank"><%- document.get('suppliedUrl') %></a>
            </p>
          <% } %>
          <% if (!document.get('secureSuppliedUrl') || !preferences.getPreference('iframe')) { %>
            <pre<%= preferences.getPreference('wrap') && ' class="wrap"' || '' %>><%- document.get('text') %></pre>
          <% } else { %>
            <iframe src="<%- document.get('secureSuppliedUrl') %>" width="100" height="100"></iframe>
          <% } %>
        </div>
      """)

    postRender:
      TwitterTweet: ->
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

    initialize: () ->
      @model.on('change:document', => @render())
      @preferences = @model.get('preferences')
      @preferences.on('change', => @render())
      @render()

    render: () ->
      document = @model.get('document')
      preferences = @model.get('preferences')

      type = document?.get('type')
      template = @templates[type]
      html = template?({
        t: t
        document: document
        preferences: @preferences
      })

      @$el.html(html)

      @postRender[type]?.call(this)

    # Scroll the view by the specified number of pages.
    #
    # 1 means scroll forward; 0 means scroll backward.
    scrollByPages: (n) ->
      # We can only scroll CSV documents.
      csv = @$('csv')[0]
      if csv?
        h = csv.clientHeight
        page_height = Math.round(Math.max(
          h * PAGE_KEY_FRACTION, h - PAGE_KEY_MARGIN
        ))
        csv.scrollTop += n * page_height

    _onClickSetPreference: (e, key, value) ->
      e.preventDefault()
      @preferences.setPreference(key, value)

    onClickEnableIframe: (e) -> @_onClickSetPreference(e, 'iframe', true)
    onClickDisableIframe: (e) -> @_onClickSetPreference(e, 'iframe', false)
    onClickEnableSidebar: (e) -> @_onClickSetPreference(e, 'sidebar', true)
    onClickDisableSidebar: (e) -> @_onClickSetPreference(e, 'sidebar', false)
    onClickEnableWrap: (e) -> @_onClickSetPreference(e, 'wrap', true)
    onClickDisableWrap: (e) -> @_onClickSetPreference(e, 'wrap', false)
