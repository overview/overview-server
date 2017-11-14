'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
const ApiBrowser = require('../lib/ApiBrowser')

describe('SearchApi', function() {
  asUserWithDocumentSet('Search/documents1.csv', function() {
    before(async function() {
      this.browser.loadShortcuts('api')

      const url = await this.browser.getUrl()
      const m = /^([^:]+:\/\/[^\/]+)\/documentsets\/(\d+)/.exec(url)
      if (!m) throw new Error(`We're at the wrong URL: ${url}`)

      const host = m[1]
      const id = m[2]

      const apiToken = await this.browser.shortcuts.api.createApiToken(id, 'search-api')

      this.api = new ApiBrowser({ baseUrl: `${host}/api/v1`, apiToken: apiToken })
      this.prefix = `/document-sets/${id}`
    })

    it('should search by q', async function() {
      const result = await this.api.GET(`${this.prefix}/documents?q=mot`)
      expect(result.items.length).to.eq(1)
      expect(result.items[0].title).to.eq('Third')
      expect(result.items[0].id & 0xffffffff).to.eq(2)
    })

    it('should search by documentIdsBitSetBase64', async function() {
      // b101000000000 => (40, 0) => oA
      const result = await this.api.GET(`${this.prefix}/documents?documentIdsBitSetBase64=oA`)
      expect(result.items.length).to.eq(2)
      expect(result.items.map(d => d.id & 0xffffffff)).to.deep.eq([ 0, 2 ])
    })

    it('should return just an Array if fields=ids', async function() {
      const result = await this.api.GET(`${this.prefix}/documents?fields=id`)
      expect(result).to.be.an('array')
      expect(result.map(i => i & 0xffffffff)).to.deep.eq([ 0, 1, 2 ])
    })
  })
})
