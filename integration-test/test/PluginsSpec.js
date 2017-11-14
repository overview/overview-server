'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('Plugins', function() {
  asUserWithDocumentSet('Metadata/basic.csv', function() {
    before(async function() {
      this.browser.loadShortcuts('documentSet')
      this.documentSet = this.browser.shortcuts.documentSet
    })

    it('should pass server, apiToken and documentSetId in the plugin query string', async function() {
      const child = await this.documentSet.createViewAndChildProcess('show-query-string')

      try {
        await this.browser.switchToFrame('view-app-iframe')
        // Wait for load. This plugin is loaded when the <pre> is non-empty
        await this.browser.assertExists({ xpath: '//pre[text() and string-length()>0]', wait: true })
        const text = await this.browser.getText({ css: 'pre' })
        expect(text).to.match(/^\?server=http%3A%2F%2Flocalhost%3A9000&documentSetId=\d+&apiToken=[a-z0-9]+$/)
        await this.browser.switchToFrame(null)
      } finally {
        child.kill()
      }

      await this.documentSet.destroyView('show-query-string')
    })
  })
})
