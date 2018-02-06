import PdfDocumentView from 'apps/DocumentDisplay/views/PdfDocumentView'

const iframeSrc = 'http://localhost:9876/base/mock-pdf-viewer/show.html'

describe('PdfDocumentView', function() {
  beforeEach(function() {
    this.div = document.createElement('div')
    document.body.appendChild(this.div)
    this.view = new PdfDocumentView({
      target: this.div,
      data: {
        iframeSrc: 'about:blank',
      },
    })

    this.iframe = this.div.querySelector('iframe')

    // Returns a Promise of a JSON lastMessage. Will wait 1s until it equals
    // `json`. If it never equals `json`, returns its last (incorrect) value.
    //
    // Usage:
    //
    //   const json = this.waitForLastMessageEqualling(sinon.match({ foo: 'bar' }))
    //   expect(json).to.deep.eq({ foo: 'bar', bar: 'baz' })
    this.waitForMessageMatching = async (sinonMatcher) => {
      return new Promise((resolve, reject) => {
        const startDate = new Date()

        const getLastMessage = () => {
          const iframeDocument = this.iframe.contentDocument
          if (!iframeDocument) return null
          const lastMessageEl = iframeDocument.querySelector('.last-message')
          if (!lastMessageEl) return null
          const lastMessageText = lastMessageEl.textContent
          if (!lastMessageText) return null
          return JSON.parse(lastMessageText)
        }

        function testOrLoop() {
          const lastMessage = getLastMessage()
          if (sinonMatcher.test(lastMessage) || (new Date() - startDate) > 1000) {
            resolve(lastMessage)
          } else {
            setTimeout(testOrLoop, 4)
          }
        }

        testOrLoop()
      })
    }
  })

  afterEach(function() {
    this.view.destroy()
    document.body.removeChild(this.div)
  })

  describe('with a PDF document', function() {
    beforeEach(function() {
      this.view.set({
        iframeSrc: iframeSrc,
        document: {
          displayType: 'pdf',
          displayUrl: '/documents/123.pdf',
          id: 123,
          pdfNotes: [],
        },
        preferences: {
          sidebar: false,
        },
      })
    })

    it('should render an iframe', function() {
      expect(this.iframe).not.to.be.null
      expect(this.iframe.getAttribute('src')).to.eq(iframeSrc)
    })

    it('should send initial state to the iframe', async function() {
      const firstMessage = await this.waitForMessageMatching(sinon.match.has('pdfNotes'))
      expect(firstMessage).to.deep.eq({
        documentId: 123,
        pdfUrl: '/documents/123.pdf',
        pdfNotes: [],
        showSidebar: false,
        highlightQ: null,
      })
    })

    it('should update showSidebar', async function() {
      this.view.set({ preferences: { sidebar: true } })
      const message = await this.waitForMessageMatching(sinon.match.has('showSidebar', true))
      expect(message).to.have.property('showSidebar', true)
    })

    it('should update documentId, pdfUrl and pdfNotes', async function() {
      const pdfNotes = [ { x: 1, y: 2, width: 3, height: 4, text: 'foo' } ]
      const displayUrl = '/localfiles/local.pdf'
      this.view.set({ document: { id: 234, displayUrl, pdfNotes } })
      const message = await this.waitForMessageMatching(sinon.match.has('documentId', 234))
      expect(message).to.include({
        documentId: 234,
        pdfUrl: displayUrl,
      })
      expect(message.pdfNotes).to.deep.eq(pdfNotes)
    })

    it('should update highlightQ', async function() {
      this.view.set({ highlightQ: 'foo' })
      const message = await this.waitForMessageMatching(sinon.match.has('highlightQ', 'foo'))
      expect(message).to.have.property('highlightQ', 'foo')
    })
  })
})
