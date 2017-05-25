TIMEOUTS = require('../TIMEOUTS')
debug_ = require('debug')('shortcuts/documentSets')

Urls =
  publicDocumentSets: '/public-document-sets'

# Shortcuts for manipulating the collection of DocumentSets for a user.
#
# Assumptions shared by all methods:
#
# * The browser URL can be anything
# * No jobs are running
# * No two document sets share the same name
module.exports = (browser) ->
  debug = (args...) -> browser.driver.call(-> debug_(args...))

  browser.loadShortcuts('jquery')
  browser.loadShortcuts('documentSet')

  # Opens a document set with the given name.
  #
  # After calling this method, the document set will be on the screen with its
  # default view, and all AJAX requests will be finished.
  open: (name) ->
    debug_("scheduling open(#{name})")
    debug("open(#{name})")
    browser
      .get('/documentsets')
      .click(link: name)
      .shortcuts.documentSet.waitUntilStable()

  # Destroys a document set.
  #
  # After calling this method, the document set will be deleted, but the client
  # will be in an unstable state. Navigate to another page to reuse the client.
  destroy: (name) ->
    debug_("scheduling destroy(#{name})")
    debug("destroy(#{name})")

    li = { tag: 'li', contains: name }

    browser
      .get('/documentsets')
      .shortcuts.jquery.waitUntilReady()
      .click([ li, { class: 'dropdown-toggle' } ])
      .click([ li, { link: 'Delete' } ])
      .alert().accept()

  # Clones a document set with the given name, producing a new one.
  #
  # After calling this method, the document set will be on the screen with its
  # default view, and all AJAX requests will be finished.
  clone: (name) ->
    debug_("scheduling clone(#{name})")
    debug("scheduling clone(#{name})")
    browser
      .get(Urls.publicDocumentSets)
      .shortcuts.jquery.waitUntilReady()
      .click([ { class: 'document-set', contains: name }, { tag: 'button', contains: 'Clone' } ])
      .shortcuts.documentSet.waitUntilStable()
