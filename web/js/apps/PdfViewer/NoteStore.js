function compareNotes(a, b) {
  return (b.pageIndex - a.pageIndex) ||
    (b.y - a.y) ||
    (a.x - b.x) ||
    (a.height - b.height) ||
    (a.width - b.width) ||
    a.text.localeCompare(b.text);
}

export default class NoteStore {
  /**
   * @param savePdfNotes function accepts { documentId, pdfNotes } and
   *                     acknowledges by calling
   *                     this.setDocumentIdAndPdfNotes() with the passed
   *                     values.
   */
  constructor({ eventBus, savePdfNotes }) {
    this.eventBus = eventBus
    this.savePdfNotes = savePdfNotes
    this.documentId = null
    this.pdfNotes = []
  }

  _savePdfNotes(newPdfNotes) {
    this.savePdfNotes({
      documentId: this.documentId,
      pdfNotes: newPdfNotes,
    })
  }

  // Stub: overview-pdf-viewer would do something here; we want this to be a
  // no-op because our app uses setDocumentIdAndPdfNotes().
  setDocumentUrl() {}

  setDocumentIdAndPdfNotes({ documentId, pdfNotes }) {
    this.documentId = documentId
    this.pdfNotes = pdfNotes
    this.eventBus.dispatch('noteschanged')
  }

  // Returns the next note.
  //
  // If `note === null`, returns the first note.
  // If there are no notes, returns `null`.
  getNextNote(note) {
    const i = this.pdfNotes.findIndex(n => note === null || compareNotes(n, note) === 0) // -1 if not found
    return this.pdfNotes[(i + 1) % this.pdfNotes.length] || null
  }

  // Returns the previous note.
  //
  // If `note === null`, returns the last note.
  // If there are no notes, returns `null`.
  getPreviousNote(note) {
    const i = this.pdfNotes.findIndex(n => note === null || compareNotes(n, note) === 0) // -1 if not found
    return this.pdfNotes[i <= 0 ? (this.pdfNotes.length - 1) : (i - 1)] || null
  }

  getNotesForPageIndex(pageIndex) {
    return this.pdfNotes.filter(n => n.pageIndex === pageIndex)
  }

  getNote(pageIndex, indexOnPage) {
    return this.getNotesForPageIndex(pageIndex)[indexOnPage]
  }

  // Schedules the addition of a note
  add(note) {
    const newPdfNotes = this.pdfNotes.slice()
    const index = this.pdfNotes.findIndex(n => compareNotes(n, note) === 0)
    if (index === -1) {
      newPdfNotes.push(note)
      newPdfNotes.sort(compareNotes)
      this._savePdfNotes(newPdfNotes)
    }

    return Promise.resolve(null)
  }

  // Schedules the deletion of a note
  deleteNote(note) {
    const newPdfNotes = this.pdfNotes.slice()
    const index = this.pdfNotes.findIndex(n => compareNotes(n, note) === 0)
    if (index !== -1) {
      newPdfNotes.splice(index, 1)
      this._savePdfNotes(newPdfNotes)
    }

    return Promise.resolve(null)
  }

  setNoteText(note, text) {
    let changed = false
    const newPdfNotes = this.pdfNotes.map(n => {
      if (compareNotes(n, note) === 0 && text !== n.text) {
        changed = true
        return {
          pageIndex: n.pageIndex,
          x: n.x,
          y: n.y,
          width: n.width,
          height: n.height,
          text: text,
        }
      } else {
        return n
      }
    })

    if (changed) {
      this._savePdfNotes(newPdfNotes)
    }

    return Promise.resolve(null)
  }
}
