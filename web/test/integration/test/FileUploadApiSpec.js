'use strict'

const debug = require('debug')('test/FileUploadApiSpec.js')

const asUser = require('../support/asUser')
const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
const ApiBrowser = require('../lib/ApiBrowser')
const TIMEOUTS = require('../lib/TIMEOUTS')

function waitFor(poll, condition, timeout) {
  return new Promise((resolve, reject) => {
    const start = new Date()

    function step() {
      poll()
        .then(value => {
          debug(value)
          if (condition(value)) {
            resolve(value)
          } else if (new Date() - start > timeout) {
            reject(new Error(`Timeout expired waiting for ${condition.toString()}`))
          } else {
            setTimeout(step, 250)
          }
        })
        .catch(err => reject(err))
    }

    step()
  })
}

describe('FileUploadApi', function() {
  asUser.usingTemporaryUser(function() {
    before(async function() {
      const b = this.browser

      await b.get('/api-tokens')
      await b.sendKeys('FileUploadApiSpec', { css: '#api-token-description', wait: true }) // wait for JS app to load
      await b.click({ button: 'Generate token' })
      this.globalToken = (await b.getText({ css: 'td.token span', wait: true })).trim()

      this.globalApi = new ApiBrowser({
        apiToken: this.globalToken,
        baseUrl: `${b.options.baseUrl}/api/v1`
      })

      this.documentSetData = await this.globalApi.POST('/document-sets', {
        title: 'FileUploadApiSpec',
        metadataSchema: { version: 1, fields: [
          { name: 'foo', type: 'String' },
          { name: 'bar', type: 'String' },
        ]},
      })
      this.documentSetId = this.documentSetData.documentSet.id

      this.apiToken = this.documentSetData.apiToken.token
      this.api = new ApiBrowser({
        apiToken: this.apiToken,
        baseUrl: `${b.options.baseUrl}/api/v1`,
      })
    })

    it('should add metadata in POST /files/finish', async function() {
      await this.api.request({
        method: 'POST',
        url: '/files/11111111-1111-1111-1111-111111111111',
        headers: {
          'Content-Disposition': 'attachment; filename=file1.txt',
          'Content-Type': 'text/plain',
          'Content-Length': 13,
        },
        body: Buffer.from('Hello, world!', 'utf-8'),
      })

      await this.api.POST('/files/finish', {
        lang: 'en',
        metadata_json: '{"foo":"bar"}',
      })

      const documents = await waitFor(
        () => this.api.GET(`/document-sets/${this.documentSetId}/documents?fields=title,metadata&refresh=true`),
        (value) => value.items.some(document => document.title === 'file1.txt'),
        TIMEOUTS.slow
      )

      const document = documents.items.find(document => document.title === 'file1.txt')
      expect(document.metadata).to.deep.eq({ foo: 'bar', bar: '' })
    })

    it('should add metadata in POST /files/:uuid', async function() {
      await this.api.request({
        method: 'POST',
        url: '/files/11111111-1111-1111-1111-111111111111',
        headers: {
          'Content-Disposition': 'attachment; filename=file2.txt',
          'Content-Type': 'text/plain',
          'Content-Length': 13,
          'Overview-Document-Metadata-JSON': '{"foo":"baz"}',
        },
        body: Buffer.from('Hello, world!', 'utf-8'),
      })

      await this.api.POST('/files/finish', { lang: 'en' })

      const documents = await waitFor(
        () => this.api.GET(`/document-sets/${this.documentSetId}/documents?fields=title,metadata&refresh=true`),
        (value) => value.items.some(document => document.title === 'file2.txt'),
        TIMEOUTS.slow
      )

      const document = documents.items.find(document => document.title === 'file2.txt')
      expect(document.metadata).to.deep.eq({ foo: 'baz', bar: '' })
    })

    it('should choose /files/:uuid metadata over /files/finish metadata when both are given', async function() {
      await this.api.request({
        method: 'POST',
        url: '/files/11111111-1111-1111-1111-111111111111',
        headers: {
          'Content-Disposition': 'attachment; filename=file3.txt',
          'Content-Type': 'text/plain',
          'Content-Length': 13,
          'Overview-Document-Metadata-JSON': '{"foo":"baz"}',
        },
        body: Buffer.from('Hello, world!', 'utf-8'),
      })

      await this.api.POST('/files/finish', {
        lang: 'en',
        metadata_json: '{ "foo": "bar", "bar": "baz" }',
      })

      const documents = await waitFor(
        () => this.api.GET(`/document-sets/${this.documentSetId}/documents?fields=title,metadata&refresh=true`),
        (value) => value.items.some(document => document.title === 'file3.txt'),
        TIMEOUTS.slow
      )

      const document = documents.items.find(document => document.title === 'file3.txt')
      expect(document.metadata).to.deep.eq({ foo: 'baz', bar: '' })
    })
  })
})
