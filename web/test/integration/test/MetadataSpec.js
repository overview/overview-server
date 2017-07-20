'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
const ApiBrowser = require('../lib/ApiBrowser')

describe('Metadata', function() {
  asUserWithDocumentSet('Metadata/basic.csv', function() {
    before(async function() {
      this.browser.loadShortcuts('api')
    })

    describe('the Show page', function() {
      before(async function() {
        this.locator = { css: '.metadata-json input[name=foo]' }
        this.locatorWithWait = { css: '.metadata-json input[name=foo]', wait: 'fast' }

        await this.browser.click({ tag: 'h3', contains: 'First' })
        await this.browser.click({ link: 'Fields', wait: 'fast' })
        await this.browser.find(this.locatorWithWait)
      })

      it('should show default metadata', async function() {
        const value = await this.browser.getAttribute(this.locator, 'value')
        expect(value).to.eq('foo0')
      })

      it('should show new metadata when browsing to a new document', async function() {
        await this.browser.click('.document-nav a.next')
        const value = await this.browser.getAttribute(this.locatorWithWait, 'value')
        expect(value).to.eq('foo1')

        // Reset state
        await this.browser.click('.document-nav a.previous')
        await this.browser.find(this.locatorWithWait)
      })

      it('should modify metadata', async function() {
        await this.browser.clear(this.locator)
        await this.browser.sendKeys('newFoo', this.locator)
        await this.browser.click('.document-nav a.next')
        await this.browser.find(this.locatorWithWait)
        await this.browser.click('.document-nav a.previous')

        const value = await this.browser.getAttribute(this.locatorWithWait, 'value')
        expect(value).to.eq('newFoo')

        // Reset state
        await this.browser.clear(this.locator)
        await this.browser.sendKeys('foo0', this.locator)

        // Browse away and back, to ensure we've saved the value
        await this.browser.click('.document-nav a.next')
        await this.browser.click('.document-nav a.previous')
      })

      it('should add and remove metadata fields', async function() {
        await this.browser.click({ link: 'Add new field' })
        await this.browser.sendKeys('baz', '.add-metadata-field input[name=name]')
        await this.browser.click('.add-metadata-field button[type=submit]')
        await this.browser.sendKeys('a baz value', '.metadata-json input[name=baz]')

        // Navigate away and come back
        await this.browser.click('.document-nav a.next')
        await this.browser.click('.document-nav a.previous')
        const value = await this.browser.getAttribute({ css: '.metadata-json input[name=baz]', wait: 'fast' }, 'value')
        expect(value).to.eq('a baz value')

        // Remove the field
        await this.browser.click('.metadata-json input[name=baz] + button.delete')
        await this.browser.alert().accept()

        // Navigate away and come back, to ensure request was sent
        await this.browser.click('.document-nav a.next')
        await this.browser.click('.document-nav a.previous')
        await this.browser.assertExists(this.locator, { wait: true })
      })

      it('should search for metadata', async function() {
        await this.browser.sendKeys("foo:foo0 OR bar:bar1", '.search input[name=query]')
        await this.browser.click('.search button')

        const text = await this.browser.getText({ css: '#document-list ul.documents', wait: 'pageLoad' })
        expect(text).to.match(/First/)
        expect(text).to.match(/Second/)
        expect(text).not.to.match(/Third/)
      })

      it('should warn when searching invalid metadata columns', async function() {
        await this.browser.click('.search a.nix')
        await this.browser.sendKeys("foo2:x", '.search input[name=query]')
        await this.browser.click('.search button')

        const text = await this.browser.getText({ css: '#document-list ul.warnings:not(:empty)', wait: 'pageLoad' })
        expect(text).to.match(/There is no “foo2” field/)
        expect(text).to.match(/text/) // a hard-coded field
        expect(text).to.match(/bar/) // a valid field
      })
    })

    describe('GET /documents', function() {
      it('should return metadata when requested', async function() {
        const url = await this.browser.getUrl()
        const m = /^([^:]+:\/\/[^\/]+)\/documentsets\/(\d+)/.exec(url)
        if (!m) throw new Error(`We're at the wrong URL: ${url}`)

        const host = m[1]
        const id = m[2]

        const apiToken = await this.browser.shortcuts.api.createApiToken(id, 'metadata')

        const api = new ApiBrowser({ baseUrl: `${host}/api/v1`, apiToken: apiToken })
        const body = await api.GET(`/document-sets/${id}/documents?fields=metadata`)
        // Body items are sorted by title, and titles are alphabetical in basic.csv
        expect(body.items.map(i => i.metadata)).to.deep.eq([
          { foo: 'foo0', bar: 'bar0' },
          { foo: 'foo1', bar: 'bar1' },
          { foo: 'foo2', bar: 'bar2' },
        ])
      })
    })
  })
})
