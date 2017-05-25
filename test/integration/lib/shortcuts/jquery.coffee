TIMEOUTS = require('../TIMEOUTS')
debug_ = require('debug')('shortcuts/jquery')

# Shortcuts that deal with AJAX requests.
module.exports = (browser) ->
  debug = (args...) -> browser.driver.call(-> debug_(args...))

  # Returns when jQuery is loaded and DOMReady has fired.
  #
  # If the site is structured such that all code loads in one big bundle, then
  # this effectively means the page is fully initialized (except for image
  # loading).
  waitUntilReady: ->
    debug_('scheduling waitUntilReady()')
    debug('waitUntilReady()')
    timeout = TIMEOUTS.fast
    browser.waitUntilBlockReturnsTrue('jQuery to load', 'pageLoad', -> window.$? && $.isReady)

  # Mark, used for waitUntilAjaxComplete().
  listenForAjaxComplete: ->
    debug_('scheduling listenForAjaxComplete()')
    debug('listenForAjaxComplete()')
    browser.execute ->
      window.listenForAjaxComplete = false
      $(document).one('ajaxComplete', -> window.listenForAjaxComplete = true)

  # Finishes when an $.ajaxComplete method is fired.
  #
  # Before calling this, you must call listenForAjaxComplete().
  # waitUntilAjaxComplete() will finish when there has been at least one
  # AJAX response between the two calls.
  #
  # Note the danger of a races. If a request was pending before you called
  # listenForAjaxComplete(), this method will finish once that pending
  # AJAX method completes. To avoid races, guarantee there are no pending AJAX
  # requests when you call listenForAjaxComplete()
  waitUntilAjaxComplete: ->
    debug_('scheduling waitUntilAjaxComplete()')
    debug('waitUntilAjaxComplete()')
    browser.waitUntilBlockReturnsTrue('AJAX request to finish', 'pageLoad', -> window.listenForAjaxComplete)
