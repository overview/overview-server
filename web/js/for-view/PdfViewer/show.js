const DefaultProps = {
  documentId: null,
  pdfUrl: null,
  fullDocumentInfo: null,
  pdfNotes: null,
  showSidebar: false,
  highlightQ: null,
  currentNoteStoreApi: null,
}

const state = Object.assign({}, DefaultProps)

/**
 * Runs a "find" for the given q. If q is empty, closes the find bar.
 */
function search(q) {
  const findBar = PDFViewerApplication.findBar
  if (!findBar) return; // PDFViewerApplication hasn't been initialized yet
  if (q) {
    findBar.open()
    findBar.caseSensitive.checked = false
    findBar.highlightAll.checked = true
    findBar.findField.value = q

    // Now -- and this is the only important part, really -- search!
    const findController = PDFViewerApplication.findController
    findController.executeCommand('find', {
      query: q,
      phraseSearch: false, // AND and OR queries break with phrase search
      caseSensitive: false,
      highlightAll: true,
      findPrevious: false,
    })
  } else {
    findBar.close()
  }
}

function openPdf() {
  PDFViewerApplication.open(state.pdfUrl, { fullDocumentInfo: state.fullDocumentInfo })
    .then(() => search(state.highlightQ))
}

function toggleSidebar(want) {
  PDFViewerApplication.pdfSidebar[want ? 'open' : 'close']()
}

function setState({ documentId, pdfUrl, fullDocumentInfo, pdfNotes, showSidebar, highlightQ}) {
  if (documentId !== undefined && documentId !== state.documentId) {
    state.documentId = documentId
  }
  if (pdfUrl !== undefined && pdfUrl !== state.pdfUrl) {
    state.pdfUrl = pdfUrl
    state.fullDocumentInfo = fullDocumentInfo
    state.currentNoteStoreApi = null
    openPdf()
  }
  if (pdfNotes !== undefined && pdfNotes !== state.pdfNotes) {
    state.pdfNotes = pdfNotes
    if (state.currentNoteStoreApi && state.documentId === state.currentNoteStoreApi) {
      state.currentNoteStoreApi.onChange(pdfNotes)
    }
  }
  if (showSidebar !== undefined && showSidebar !== state.showSidebar) {
    state.showSidebar = showSidebar
    toggleSidebar(showSidebar)
  }
  if (highlightQ !== undefined && highlightQ !== state.highlightQ) {
    state.highlightQ = highlightQ
    search(highlightQ)
  }
}

function beginCreatePdfNote() {
  PDFViewerApplication.eventBus.dispatch('toggleaddingnote')
}

function goToPdfNote(pdfNote) {
  // TODO make this work when "full document" is loaded (see
  // the "partial document"/"full document" feature of overview-pdf-viewer)
  PDFViewerApplication.editNoteTool.setNote(pdfNote)
}

window.addEventListener('message', function(ev) {
  if (ev.origin !== window.origin) {
    console.log('Ignoring message with wrong origin', ev)
    return
  }

  const message = ev.data

  switch (message.call) {
    case 'setState':
      setState(message.state)
      break
    case 'beginCreatePdfNote':
      beginCreatePdfNote()
      break
    case 'goToPdfNote':
      goToPdfNote(message.pdfNote)
      break
    default:
      console.warn('Ignoring unhandled message', message)
  }
})

class ErrorNoteStore {
  constructor(message) {
    console.error(message)
    this.message = message
    this.load = this.load.bind(this)
    this.save = this.save.bind(this)
  }

  load() { return Promise.resolve([]) }
  save(notes) { return Promise.reject(new Error(this.message)) }
}

class NoteStoreApi {
  constructor(documentId, onChange) {
    this.documentId = documentId;
    this.onChange = onChange;
    this.load = this.load.bind(this)
    this.save = this.save.bind(this)
  }

  load() {
    if (state.documentId !== this.documentId) {
      return Promise.resolve([])
    }
    return Promise.resolve(state.pdfNotes)
  }

  save(notes) {
    window.parent.postMessage({
      call: 'fromPdfViewer:savePdfNotes',
      documentId: this.documentId,
      pdfNotes: notes,
    }, window.origin)
    // Pretend save succeeds immediately -- as though we're synchronous
    return Promise.resolve(null)
  }
}

window.addEventListener('webviewerloaded', function() {
  PDFViewerApplication.sidebarViewOnLoad = state.showSidebar ? 1 : 0;

  PDFViewerApplication.noteStoreApiCreator = (url, { onChange }) => {
    if (url !== state.pdfUrl) {
      return new ErrorNoteStore(
        `Requested note store for ${url} but we are viewing ${state.pdfurl}`
      )
    }
    if (state.documentId === null) {
      return new ErrorNoteStore(
        `Requested note store for ${url} but we are not viewing a document`
      )
    }
    state.currentNoteStoreApi = new NoteStoreApi(state.documentId, onChange)
    return state.currentNoteStoreApi
  }

  (() => {
    // Replaces {{arguments}} with their values.
    function formatL10nValue(text, args) {
      if (!args) {
        return text;
      }
      return text.replace(/\{\{\s*(\w+)\s*\}\}/g, (all, name) => {
        return name in args ? args[name] : "{{" + name + "}}";
      });
    }

    // Test "NullL10n" -- don't download any external files
    const NullL10n = {
      getLanguage: () => Promise.resolve("en-us"),
      getDirection: () => Promise.resolve("ltr"),
      get: (property, args, fallback) => Promise.resolve(formatL10nValue(fallback, args)),
      translate: (element) => Promise.resolve(undefined),
    }

    PDFViewerApplication.externalServices.createL10n = () => NullL10n
  })()

  // Load document once PDFJS has loaded
  window.parent.postMessage({
    call: 'fromPdfViewer:getState'
  }, window.origin)
})
