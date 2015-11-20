TIMEOUTS = require('../TIMEOUTS')
debug_ = require('debug')('shortcuts/documentSet')

clientTests =
  noJobsInProgress: -> $?.isReady && !$('progress').length # needs jQuery check because page refreshes
  documentListLoaded: -> !!$('#document-list:not(.loading)').length
  pluginDataLoaded: -> !!$('a[data-plugin-url="about:tree"]').length

# Shortcuts for doing stuff while on the DocumentSet page.
#
# Assumptions shared by all methods:
#
# * You are on the document set page, and the page has stabilized. (Use
#   documentSets#open() to reach this state.)
module.exports = (browser) ->
  debug = (args...) -> browser.driver.call(-> debug_(args...))

  # Destroys the view with the given name.
  #
  # After completion, the server will have deleted the document set but the
  # client will be in an undefined state. You must open another page to
  # continue using it.
  destroyView: (name) ->
    debug_("scheduling destroyView(#{name})")
    debug("destroyView(#{name})")
    browser
      .click([ { link: name }, { class: 'toggle-popover' } ])
      .click(button: 'Delete')
      .alert().accept()
    browser

  # Creates a new Tree view with the given name.
  #
  # options.tag sets an optional tag name. If unset, all documents will be
  # reclustered.
  recluster: (name, options={}) ->
    debug_("scheduling recluster(#{name}, #{JSON.stringify(options)})")
    debug("recluster(#{name}, #{JSON.stringify(options)})")
    browser
      .click(link: 'Add view')
      .click(css: 'a[data-plugin-url="about:tree"]')
      .sendKeys(name, css: 'input[name=tree_title]', wait: true) # wait because modal fades in

    if options.tag?
      browser.click(tag: 'option', contains: options.tag, wait: true) # wait because tags come in an AJAX response

    browser
      .click(button: 'Create Tree')
      .shortcuts.documentSet.waitUntilStable()

  # Renames a view
  renameView: (oldTitle, newTitle) ->
    debug_("scheduling renameView(#{oldTitle}, #{newTitle})")
    debug("renameView(#{oldTitle}, #{newTitle})")
    browser
      .click([ { link: oldTitle }, { class: 'toggle-popover' } ])
      .click(link: 'rename', wait: true) # wait for popover to appear
      .sendKeys(newTitle, css: 'input[name=title]', wait: true) # wait for form to appear
      .shortcuts.jquery.listenForAjaxComplete()
      .click(button: 'Save')
      .shortcuts.jquery.waitUntilAjaxComplete()
      .click(link: 'Close')

  renameDocument: (oldTitle, newTitle) ->
    debug_("scheduling renameDocument(#{oldTitle}, #{newTitle})")
    debug("renameDocument(#{oldTitle}, #{newTitle})")
    browser
      .click(tag: 'h3', contains: oldTitle)
      .click(css: 'header .edit-title', wait: true) # wait for popover to appear
      .sendKeys(newTitle, css: 'input[name=title]')
      .shortcuts.jquery.listenForAjaxComplete()
      .click(button: 'Save')
      .shortcuts.jquery.waitUntilAjaxComplete()

  search: (q) ->
    debug_("scheduling search(#{q})")
    debug("search(#{q})")
    browser
      .sendKeys(q, css: '#document-list-params .search input[name=query]')
      .click(css: '#document-list-params .search button')

  # Waits for #document-list:not(.loading) to exist.
  waitUntilDocumentListLoaded: ->
    debug_('scheduling waitUntilDocumentListLoaded()')
    debug('waitUntilDocumentListLoaded()')
    browser.waitUntilBlockReturnsTrue('document list to load', 'pageLoad', clientTests.documentListLoaded)
    browser

  # Waits until the document set page is completely stable.
  #
  # This means:
  #
  # * The plugin list has been loaded.
  # * A document list is visible.
  # * All pending jobs are finished.
  waitUntilStable: ->
    debug_('scheduling waitUntilStable()')
    debug('waitUntilStable()')

    browser.shortcuts.jquery.waitUntilReady()
    browser.waitUntilBlockReturnsTrue('jobs to complete', 'slow', clientTests.noJobsInProgress)
    browser.waitUntilBlockReturnsTrue('document list to load', 'pageLoad', clientTests.documentListLoaded)
    browser.waitUntilBlockReturnsTrue('plugin data to load', 'pageLoad', clientTests.pluginDataLoaded)

    browser
