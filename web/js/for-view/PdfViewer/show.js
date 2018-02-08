import NoteStore from 'apps/PdfViewer/NoteStore'

const DefaultProps = {
  documentId: null,
  pdfUrl: null,
  pdfNotes: null,
  showSidebar: false,
  highlightQ: null,
}

const state = Object.assign({}, DefaultProps)

/**
 * Runs a "find" for the given q. If q is empty, closes the find bar.
 */
function search(q) {
  const findBar = PDFViewerApplication.findBar
  if (q) {
    findBar.open()
    findBar.caseSensitive.checked = false
    findBar.highlightAll.checked = true
    findBar.findField.value = q

    // Now -- and this is the only important part, really -- search!
    findController = PDFViewerApplication.findController
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
  PDFViewerApplication.open(state.pdfUrl, null)
  search(state.highlightQ)
}

function toggleSidebar(want) {
  PDFViewerApplication.pdfSidebar[want ? 'open' : 'close']()
}

function setNotes(documentId, pdfNotes) {
  // Uses our custom NoteStore
  PDFViewerApplication.noteStore.setDocumentIdAndPdfNotes({ documentId, pdfNotes })
}

function setState({ documentId, pdfUrl, pdfNotes, showSidebar, highlightQ}) {
  if (documentId !== undefined && documentId !== state.documentId) {
    state.documentId = documentId
  }
  if (pdfUrl !== undefined && pdfUrl !== state.pdfUrl) {
    state.pdfUrl = pdfUrl
    openPdf()
  }
  if (pdfNotes !== undefined && pdfNotes !== state.pdfNotes) {
    state.pdfNotes = pdfNotes
    setNotes(state.documentId, state.pdfNotes)
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

// When you open a sidebar, PDFJS remembers which siebar you opened. That's
// bad if the user turns off the sidebar. So we'll implement this logic:
//
// * If state.showSidebar is true, initially open PDFJS's stored view for the
//   document (the `view` parameter) _or_ the last-opened view
//   (pdfSidebar.active) _or_ the default, THUMBS (also pdfSidebar.active).
// * If state.showSidebar is false, force-hide sidebar.
function monkeyPatchPdfSidebarSetInitialViewToFollowOverviewSidebarPreference() {
  const pdfSidebar = PDFViewerApplication.pdfSidebar
  const originalSetInitialView = pdfSidebar.setInitialView.bind(pdfSidebar)
  pdfSidebar.setInitialView = function(view) {
    if (state.showSidebar) {
      originalSetInitialView(view || this.active)
    } else {
      originalSetInitialView(0)
    }
  }
}

window.addEventListener('load', function() {
  monkeyPatchPdfSidebarSetInitialViewToFollowOverviewSidebarPreference()
  PDFViewerApplication.noteStore = PDFViewerApplication.pdfViewer.noteStore = new NoteStore({
    eventBus: PDFViewerApplication.eventBus,
    savePdfNotes: ({ documentId, pdfNotes }) => {
      window.parent.postMessage({
        call: 'fromPdfViewer:savePdfNotes',
        documentId: documentId,
        pdfNotes: pdfNotes,
      }, document.location.origin)
    }
  })

  // Load document once PDFJS has loaded
  window.parent.postMessage({
    call: 'fromPdfViewer:getState'
  }, window.origin)
})
