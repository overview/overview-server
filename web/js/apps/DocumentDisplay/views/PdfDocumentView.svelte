<div class="pdf-document-view"
  ><iframe title="" ref:iframe id="document-contents" src={{iframeSrc}}></iframe
></div>

<script>
  function documentToPdfViewerState(document) {
    return {
      documentId: document ? document.id : null,
      pdfUrl: document ? document.displayUrl : null,
      pdfNotes: document ? document.pdfNotes : null,
    }
  }

  export default {
    data() {
      return {
        document: null,
        preferences: null,
        highlightQ: null,
        iframeSrc: '/pdf-viewer',
      }
    },

    computed: {
      showSidebar: preferences => preferences && preferences.sidebar || false,
    },

    methods: {
      beginCreatePdfNote() {
        this.refs.iframe.contentWindow.postMessage({
          call: 'beginCreatePdfNote',
        }, document.origin)
      },
    },

    oncreate() {
      // frameProps: State we keep handy in case we receive 'fromPdfViewer:getState'
      // We may receive that message at any time once the iframe is created. (The
      // intent is to receive it on page load; but we have no control over that.)
      const iframeProps = Object.assign({
        showSidebar: this.get('showSidebar'),
        highlightQ: this.get('highlightQ'),
      }, documentToPdfViewerState(this.get('document')))

      const iframe = this.refs.iframe

      function sendStateToIframe(state) {
        iframe.contentWindow.postMessage({
          call: 'setState',
          state: state,
        }, document.origin)
      }

      function updateIframeProps(state) {
        Object.assign(iframeProps, state)
        sendStateToIframe(state)
      }

      this.observe('document', document => {
        updateIframeProps(documentToPdfViewerState(document))
      })

      this.observe('highlightQ', highlightQ => {
        updateIframeProps({ highlightQ })
      })

      this.observe('showSidebar', showSidebar => {
        updateIframeProps({ showSidebar })
      })

      this.messageListener = (ev) => {
        if (ev.origin === document.origin && ev.source === iframe.contentWindow) {
          const message = ev.data
          switch (message.call) {
            case 'fromPdfViewer:getState':
              sendStateToIframe(iframeProps)
              break
            case 'fromPdfViewer:savePdfNotes':
              this.fire('changePdfNotes', {
                documentId: message.documentId,
                pdfNotes: message.pdfNotes,
              })
              break
            default:
              console.warn('Unhandled message from PDF Viewer iframe', ev)
          }
        }
      }
      window.addEventListener('message', this.messageListener)
    },

    ondestroy() {
      window.removeEventListener('message', this.messageListener)
    }
  }
</script>
