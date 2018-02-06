import PdfDocumentView from 'apps/DocumentDisplay/views/PdfDocumentView'

const iframeSrc = 'http://localhost:9876/base/mock-pdf-viewer/show.html'

// Will wait for the predicate to return true, or for 1s to elapse:
// whichever comes first
function waitForPredicateOrNoOp(predicate) {
  return new Promise((resolve, reject) => {
    const startDate = new Date()

    function testOrLoop() {
      if (predicate() || (new Date() - startDate) > 1000) {
        resolve(null)
      } else {
        setTimeout(testOrLoop, 4)
      }
    }

    testOrLoop()
  })
}

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
      const getMessage = () => {
        const iframeDocument = this.iframe.contentDocument
        if (!iframeDocument) return null
        const lastMessageEl = iframeDocument.querySelector('.last-message')
        if (!lastMessageEl) return null
        const lastMessageText = lastMessageEl.textContent
        if (!lastMessageText) return null
        return JSON.parse(lastMessageText)
      }

      return waitForPredicateOrNoOp(() => {
        const json = getMessage()
        return json !== null && sinonMatcher.test(json)
      })
        .then(() => getMessage())
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

    it('should fire changePdfNotes', async function() {
      const pdfNotes = [
        { pageIndex: 0, x: 1, y: 2, width: 3, height: 4, text: 'text', },
      ]

      // Wait for load. After this, we know we can use callPostMessage() from iframe
      const message = await this.waitForMessageMatching(sinon.match.has('documentId'))
      expect(message).to.have.property('documentId')

      const spy = sinon.spy()
      this.view.on('changePdfNotes', spy)

      this.iframe.contentWindow.callPostMessage({
        call: 'fromPdfViewer:savePdfNotes',
        documentId: 123,
        pdfNotes: pdfNotes,
      })

      const matcher = sinon.match({
        documentId: 123,
        pdfNotes: pdfNotes,
      })
      await waitForPredicateOrNoOp(() => spy.called)
      expect(spy).to.have.been.calledWith(matcher)
    })

    it('should update highlightQ', async function() {
      this.view.set({ highlightQ: 'foo' })
      const message = await this.waitForMessageMatching(sinon.match.has('highlightQ', 'foo'))
      expect(message).to.have.property('highlightQ', 'foo')
    })
  })
})
