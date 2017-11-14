'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('Plugins', function() {
  asUserWithDocumentSet('Metadata/basic.csv', function() {
    before(async function() {
      this.browser.loadShortcuts('documentSet')
      this.documentSet = this.browser.shortcuts.documentSet
    })

    it('should pass server, apiToken and documentSetId in the plugin query string', async function() {
      const server = await this.documentSet.createViewAndServer('show-query-string')

      try {
        await this.browser.switchToFrame('view-app-iframe')
        // Wait for load. This plugin is loaded when the <pre> is non-empty
        await this.browser.assertExists({ xpath: '//pre[text() and string-length()>0]', wait: true })
        const text = await this.browser.getText({ css: 'pre' })
        expect(text).to.match(/^\?server=http%3A%2F%2Flocalhost%3A9000&documentSetId=\d+&apiToken=[a-z0-9]+$/)
        await this.browser.switchToFrame(null)
      } finally {
        await server.close()
      }

      await this.documentSet.destroyView('show-query-string')
    })

    describe('with a plugin that calls setRightPane', async function() {
      before(async function() {
        this.server = await this.documentSet.createViewAndServer('right-pane')
      })

      after(async function() {
        await this.server.close()
      })

      it('should create a right pane', async function() {
        await this.browser.assertNotExists({ id: 'tree-app-vertical-split-2' }) // it's invisible

        // wait for load
        await this.browser.switchToFrame('view-app-iframe')
        await this.browser.assertExists({ css: 'body.loaded', wait: 'slow' })
        await this.browser.click({ button: 'Set Right Pane' })
        await this.browser.switchToFrame(null)

        await this.browser.assertExists({ id: 'tree-app-vertical-split-2', wait: true }) // wait for animation
        await this.browser.click({ css: '#tree-app-vertical-split-2 button' })
        await this.browser.assertExists({ id: 'view-app-right-pane-iframe' })
        await this.browser.switchToFrame('view-app-right-pane-iframe')
        const url = await this.browser.execute(function() { return window.location.href })
        expect(url).to.eq('http://localhost:3333/show?placement=right-pane')
        await this.browser.switchToFrame(null)
      })
    })
  })
})
