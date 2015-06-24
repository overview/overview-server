debug_ = require('debug')('shortcuts/api')

# Shortcuts for manipulating API tokens.
#
# These methods make no assumptions about browser state.
module.exports = (browser) ->
  debug = (args...) -> browser.driver.call(-> debug_(args...))

  browser.loadShortcuts('jquery')

  # Creates an API token and returns it in a Promise
  createApiToken: (documentSetId, keyName) ->
    debug_("scheduling createApiToken(#{documentSetId})")
    debug("createApiToken(#{documentSetId})")
    browser
      .get("/documentsets/#{documentSetId}/api-tokens")
      .waitUntilBlockReturnsTrue 'api-token page to load', 'pageLoad', ->
        $?.isReady && !!$('#api-token-description').length
      .sendKeys(keyName, { id: 'api-token-description' })
      .shortcuts.jquery.listenForAjaxComplete()
      .click(button: 'Generate token')
      .shortcuts.jquery.waitUntilAjaxComplete()
      .find([ { tag: 'tr', contains: keyName }, { tag: 'td', class: 'token' } ]).then((el) -> el.getText())
