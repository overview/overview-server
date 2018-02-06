<div class="document-view-twitter">
  <div ref:container class="twitter-tweet-container"></div>
  <div class="loading">{{t('loading')}}</div>
</div>

<script>
  // CSV you can upload to localhost or production to test with real Twitter:
  // url,text
  // https://twitter.com/adamhooper/status/910855864239759360,Tweet One
  // https://twitter.com/adamhooper/status/910795797217804288,Tweet Two
  // https://twitter.com/realDonaldTrump/status/952183452366929920,Here is deleted content

  import i18n from 'i18n'

  const t = i18n.namespaced('views.Document.show.DocumentView.twitter')

  // Mess with "window.twttr" global variable: we want one Twitter Widgets JS
  // URL in unit tests, another on production.
  function twttrReady(scriptUrl, callback) {
    if (!twttrReady.scriptUrl) {
      if (window.twttr) {
        throw new Error('TwitterDocumentView has not rendered a tweet, but window.twttr is somehow available.')
      }

      twttrReady.scriptUrl = scriptUrl

      // https://dev.twitter.com/web/javascript/loading
      //
      // Differences:
      // * We let the user override the URL
      // * We don't bother with a tag ID
      // * This code should be more legible
      const s = document.createElement('script')
      s.src = scriptUrl
      document.querySelector('head').appendChild(s)

      const t = window.twttr = { _e: [] }
      t.ready = function(f) {
        t._e.push(f)
      }
    } else if (twttrReady.scriptUrl !== scriptUrl) {
      throw new Error('TwitterDocumentView can only be used on one scriptUrl per page load, because it uses the window.twttr global variable.')
    }

    window.twttr.ready(() => callback(window.twttr))
  }
  twttrReady.scriptUrl = null

  function renderTweet(scriptUrl, el, statusId) {
    // This is a bit complicated, because of asynchronicity.
    //
    // Twitter's twtter.widgets.createTweet() will render the tweet, but we
    // need to wait for Twitter to load. So we wrap our code in twttrReady().
    // But _then_ we need to worry that the user might _switch_ tweets after
    // we've queued a render. If that happens, we should cancel the first
    // render because the DOM element is gone -- but we shouldn't call
    // twttrReady() again because we know that the previous call is still
    // going to run its callback.

    // 1. Rearrange the render queue.
    if (statusId) {
      // Queue at most one render at a time
      renderTweet.queue[0] = { el, statusId }
    } else {
      // When an element disappears, empty the render queue. Don't render atop
      // elements that aren't in the DOM.
      renderTweet.queue.splice(0, renderTweet.queue.length)
    }

    if (!renderTweet.waiting) {
      renderTweet.waiting = true
      renderTweet.scheduleRenders(scriptUrl)
    }
  }
  renderTweet.waiting = false
  renderTweet.queue = []
  renderTweet.scheduleRenders = function(scriptUrl) {
    twttrReady(scriptUrl, renderTweet.doRenders)
  }
  renderTweet.doRenders = function(twttr) {
    while (renderTweet.queue.length) {
      const { el, statusId } = renderTweet.queue.shift()
      el.innerHTML = '' // Clear previous tweets
      // synchronously, write "data-tweet-id" attribute.
      // The last-set data-tweet-id attribute is the one that belongs.
      el.setAttribute('data-tweet-id', statusId)
      twttr.widgets.createTweet(statusId, el, { width: el.clientWidth })
        .then(renderedEl => {
          // Avoid race: delete tweets with the wrong data-tweet-id. They were
          // created by _previous_ render calls.
          //
          // Render calls come out of order. The place we store the most-recent
          // tweet ID is in the parent data-tweet-id.
          const wantedTweetId = el.getAttribute('data-tweet-id')
          if (wantedTweetId !== statusId) {
            // We just rendered tweet statusId, but too late! There's another
            // tweet being rendered now.

            if (!renderedEl) {
              // For deleted tweets (renderedEl === undefined), Twitter inserts
              // an element but doesn't return it. Delete that.
              //
              // We don't unit-test this. Be wary when editing.
              renderedEl = Array.from(el.childNodes).find(child => {
                return child.getAttribute('data-tweet-id') === wantedTweetId
              })
            }

            // Sometimes renderedEl.parentNode !== el. Avoid a DOMException by
            // checking.
            if (renderedEl && renderedEl.parentNode === el) el.removeChild(renderedEl)
            return
          }

          if (!renderedEl) {
            // It's a deleted tweet. Render "deleted"
            const div = document.createElement('div')
            div.className = 'deleted'
            div.textContent = t('deleted')
            el.insertBefore(div, renderedEl)
          }
        })
        .catch(err => console.warn(err))
    }
    renderTweet.waiting = false
  }

  export default {
    data() {
      return {
        document: null,
        twitterWidgetsUrl: 'https://platform.twitter.com/widgets.js', // https://dev.twitter.com/web/javascript/loading
        t: t,
      }
    },

    computed: {
      url: (document) => document ? document.displayUrl : null,

      statusId: (url) => url && url.replace(/.*\//, '') || null, // statusId is the last part of the URL
    },

    oncreate() {
      this.observe('statusId', (statusId) => {
        renderTweet(this.get('twitterWidgetsUrl'), this.refs.container, statusId)
      })
    }
  }
</script>
