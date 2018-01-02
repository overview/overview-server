'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
const asUserWithPdfDocumentSet = require('../support/asUserWithPdfDocumentSet')

describe('Plugins', function() {
  asUserWithDocumentSet('Metadata/basic.csv', function() {
    beforeEach(function() {
      this.browser.loadShortcuts('documentSet')
      this.browser.loadShortcuts('jquery')
      this.documentSet = this.browser.shortcuts.documentSet
    })

    it('should pass server, apiToken and documentSetId in the plugin query string', async function() {
      const server = await this.documentSet.createViewAndServer('show-query-string')

      try {
        await this.browser.switchToFrame('view-app-iframe')
        // Wait for load. This plugin is loaded when the <pre> is non-empty
        await this.browser.assertExists({ xpath: '//pre[text() and string-length()>0]', wait: true })
        const text = await this.browser.getText({ css: 'pre' })
        expect(text).to.match(/^\?server=http%3A%2F%2F[-\w.]+(?:%3A\d+)?&documentSetId=\d+&apiToken=[a-z0-9]+$/)
        await this.browser.switchToFrame(null)
      } finally {
        await server.close()
      }

      await this.documentSet.destroyView('show-query-string')
    })

    describe('with a plugin that calls notifyApi', async function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('notify-api')
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should respond with serverUrlFromClient and serverUrlFromPlugin', async function() {
        const b = this.browser
        const preText = await b.inFrame('view-app-iframe', async () => {
          await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
          return await b.getText('pre')
        })
        const preJson = JSON.parse(preText)
        // TODO integration-test what happens when these are different
        //
        // To do that we'd need an "advanced" plugin-creation UI, so users could
        // set serverUrlFromPlugin.
        expect(preJson.serverUrlFromClient).to.deep.eq(process.env.OVERVIEW_URL)
        expect(preJson.serverUrlFromPlugin).to.deep.eq(process.env.OVERVIEW_URL)
      })
    })

    describe('with a plugin that calls setRightPane', async function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('right-pane')
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should create a right pane', async function() {
        const b = this.browser

        await b.assertNotExists({ id: 'tree-app-vertical-split-2' }) // it's invisible

        // wait for load
        await b.inFrame('view-app-iframe', async () => {
          await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
          await b.click({ button: 'Set Right Pane' })
        })

        await b.assertExists({ id: 'tree-app-vertical-split-2', wait: true }) // wait for animation
        await b.click({ css: '#tree-app-vertical-split-2 button' })
        await b.sleep(1000) // for animation
        await b.assertExists({ id: 'view-app-right-pane-iframe' })

        const url = await b.inFrame('view-app-right-pane-iframe', async () => {
          return await b.execute(function() { return window.location.href })
        })
        expect(url).to.contain('?placement=right-pane')
      })

      it('should delete the right pane when deleting the view', async function() {
        const b = this.browser

        // 1. create the right pane
        await b.inFrame('view-app-iframe', async () => {
          await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
          await b.click({ button: 'Set Right Pane' })
        })

        await b.assertExists({ css: '#tree-app-vertical-split-2 button', wait: true }) // wait for animation

        // 2. delete the view
        await b.shortcuts.documentSet.destroyView('right-pane')

        // Check the pane is gone
        await b.assertNotExists({ css: '#tree-app-vertical-split-2 button', wait: true })
        await b.assertNotExists({ id: 'view-app-right-pane-iframe', wait: true })
      })
    })

    describe('with a plugin that calls setModalDialog', async function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('modal-dialog')
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should create and close a modal dialog', async function() {
        const b = this.browser

        await b.assertNotExists({ id: 'view-app-modal-dialog' })

        await b.inFrame('view-app-iframe', async () => {
          // wait for load
          await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
          await b.click({ button: 'Set Modal Dialog' })
        })

        await b.assertExists({ id: 'view-app-modal-dialog', wait: true })

        await b.inFrame('view-app-modal-dialog-iframe', async () => {
          await b.click({ button: 'Set Modal Dialog to Null', wait: 'pageLoad' })
        })

        await b.assertNotExists({ id: 'view-app-modal-dialog' })
      })

      it('should send messages from one plugin to another', async function() {
        const b = this.browser

        await b.inFrame('view-app-iframe', async () => {
          // wait for load
          await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
          await b.click({ button: 'Set Modal Dialog' })
        })

        await b.assertExists({ id: 'view-app-modal-dialog', wait: true })

        await b.inFrame('view-app-modal-dialog-iframe', async () => {
          await b.click({ button: 'Send Message', wait: 'pageLoad' })
        })

        await b.inFrame('view-app-iframe', async () => {
          await b.assertExists({ tag: 'pre', contains: '{"This is":"a message"}', wait: true })
        })
      })
    })

    describe('with a plugin that calls setViewFilter', async function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('view-filter')
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should allow filtering by view', async function() {
        const b = this.browser

        // Wait for iframe contents to load and call setViewFilter
        await b.click({ tag: 'a', contains: 'view-filter placeholder', wait: 'pageLoad' })
        await b.click({ tag: 'span', contains: 'VF-Foo' })
        await this.documentSet.waitUntilDocumentListLoaded()
        expect(this.server.lastRequestUrl.path).to.match(/^\/filter\/01010101\?/)
        expect(this.server.lastRequestUrl.query.apiToken || '').to.match(/[a-z0-9]+/)
        expect(this.server.lastRequestUrl.query.ids).to.eq('foo')
        expect(this.server.lastRequestUrl.query.operation).to.eq('any')
        const text = await(b.getText('#document-list ul.documents'))
        expect(text).not.to.match(/First/)
        expect(text).to.match(/Second/)
        expect(text).not.to.match(/Third/)
      })

      it('should allow setViewFilterChoices', async function() {
        const b = this.browser

        await b.inFrame('view-app-iframe', async () => {
          // Wait for iframe contents to load
          await b.click({ button: 'setViewFilterChoices', wait: 'pageLoad' })
          await b.sleep(100) // make sure postMessage() goes through. TODO notify from plugin?
        })

        await b.click({ tag: 'a', contains: 'view-filter placeholder' })
        await b.click({ tag: 'span', contains: 'VF-Foo2' }) // assert it exists, really
      })

      it('should remove the ViewFilter when deleting the plugin', async function() {
        await this.browser.shortcuts.documentSet.destroyView('view-filter')
        await this.browser.assertNotExists({ tag: 'a', contains: 'view-filter placeholder', wait: true })
      })

      it('should allow setViewFilterSelection', async function() {
        const b = this.browser

        await b.inFrame('view-app-iframe', async () => {
          // Wait for iframe contents to load
          await b.click({ button: 'setViewFilterChoices', wait: 'pageLoad' })
          await b.click({ button: 'setViewFilterSelection' })
          await b.sleep(100) // make sure postMessage() goes through. TODO notify from plugin?
        })

        await this.documentSet.waitUntilDocumentListLoaded()
        expect(this.server.lastRequestUrl.path).to.match(/^\/filter\/01010101\?/) // the document list reloaded
        await b.assertExists({ tag: 'a', contains: 'VF-Foo2' }) // the tag was selected in the ViewFilter
      })
    })

    describe('with a plugin that calls setDocumentDetailLink', function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('view-document-detail-links')

        await this.browser.shortcuts.documentSet.openDocumentFromList('First')

        this.clickViewButton = async function(name) {
          const b = this.browser
          await b.inFrame('view-app-iframe', async () => {
            await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
            await b.click({ button: name })
          })
        }
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should add the given link', async function() {
        const b = this.browser

        await this.clickViewButton("setUrl(foo)")
        await b.assertExists({ link: 'Text foo', wait: true })
      })

      it('should show the link even after page refresh', async function() {
        const b = this.browser

        await this.clickViewButton("setUrl(foo)")
        await b.assertExists({ link: 'Text foo', wait: true }) // wait for it to appear

        await b.refresh()
        await this.documentSet.waitUntilStable()
        // again, open a document
        await this.documentSet.openDocumentFromList('First')
        await b.assertExists({ link: 'Text foo', wait: true })
      })

      it('not create a duplicate link (to the same URL)', async function() {
        const b = this.browser

        await this.clickViewButton("setUrl(foo)")
        await b.assertExists({ link: 'Text foo', wait: true }) // wait for it to appear

        // waitUntilAjaxComplete: make sure we have 0 ajax requests (for next stuff)
        await b.shortcuts.jquery.listenForAjaxComplete()
        await this.documentSet.createCustomView('another view', `http://${this.server.hostname}:3333`)
        await b.shortcuts.jquery.waitUntilAjaxComplete()

        // Add the same URL again. Wait for it to complete. (That's an ajax request;
        // aren't you glad we're sure there were zero before?)
        await b.shortcuts.jquery.listenForAjaxComplete()
        await this.clickViewButton("setUrl(foo, foo with different text)")
        await b.shortcuts.jquery.waitUntilAjaxComplete()
        // Ajax is done. That means the new link has been saved to the server
        // and rendering is definitely finished. But since the URL is the same,
        // we expect nothing else to be rendered
        await b.assertNotExists({ link: 'foo with different text' })
      })

      it('should open the popup', async function() {
        const b = this.browser

        await this.clickViewButton("setUrl(foo)")
        await b.click({ link: 'Text foo', wait: true }) // wait for it to appear

        await b.assertExists({ css: 'iframe#view-document-detail', wait: 'fast' }) // wait for iframe
        const url = await b.inFrame('view-document-detail', async () => {
          return await this.browser.execute(function() { return window.location.href })
        })
        expect(url).to.match(/\?documentId=\d+/)
        expect(url).to.match(/&foo=foo/)
      })

      it('should remove the link when deleting the View', async function() {
        const b = this.browser
        await this.clickViewButton('setUrl(foo)')
        await b.assertExists({ link: 'Text foo', wait: true })
        await b.shortcuts.documentSet.destroyView('view-document-detail-links')
        await b.assertNotExists({ link: 'Text foo', wait: 'pageLoad' })
      })
    })

    describe('with a plugin that calls setViewTitle', function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('set-view-title')
        this.clickViewButton = async function(name) {
          const b = this.browser
          await b.inFrame('view-app-iframe', async () => {
            await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
            await b.click({ button: name })
          })
        }
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should set the view title', async function() {
        const b = this.browser

        await this.clickViewButton("Set title to new-title")
        await b.assertExists({ tag: 'li', className: 'view', contains: 'new-title', wait: 'fast' })
      })

      it('should preserve the view title across page refresh', async function() {
        const b = this.browser

        await this.clickViewButton("Set title to new-title")
        await b.assertExists({ tag: 'li', className: 'view', contains: 'new-title', wait: 'fast' })

        await b.refresh()
        await this.documentSet.waitUntilStable()
        await b.assertExists({ tag: 'li', className: 'view', contains: 'new-title', wait: 'fast' })
      })
    })
  })

  asUserWithPdfDocumentSet('PdfAnnotations', function() {
    beforeEach(function() {
      this.browser.loadShortcuts('documentSet')
      this.browser.loadShortcuts('jquery')
      this.documentSet = this.browser.shortcuts.documentSet
    })

    describe('with a plugin that interacts with PdfNotes', function() {
      beforeEach(async function() {
        this.server = await this.documentSet.createViewAndServer('pdf-notes')
        this.clickViewButton = async function(name) {
          const b = this.browser
          await b.inFrame('view-app-iframe', async () => {
            await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
            await b.click({ button: name })
          })
        }
      })

      afterEach(async function() {
        if (this.server) await this.server.close()
      })

      it('should begin PdfNote creation', async function() {
        const b = this.browser

        await b.shortcuts.documentSet.openDocumentFromList('doc1.pdf')
        await this.clickViewButton('Create PDF Note')

        await b.inFrame('document-contents', async () => {
          await b.assertExists({ css: 'button.addNote.toggled', wait: true })
        })
      })
    })
  })
})
