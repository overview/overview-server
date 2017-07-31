'use strict'

const Backbone = require('backbone')

function runCallbacks(callbacks, value) {
  callbacks.forEach(function(callback) { callback(value) })
}

module.exports = class Api {
  constructor(options) {
    if (!options.state) throw 'Must pass options.state, a State'
    if (!options.globalActions) throw 'Must pass options.globalActions, an Object of functions'

    // Inherit from Backbone.Events
    for (const f in Backbone.Events) {
      this[f] = Backbone.Events[f]
    }

    this.state = options.state
    this.globalActions = options.globalActions

    this._documentSetChanged = []
    this._documentChanged = []

    const listenToDocumentSet = () => {
      const notify = (documentSet) => runCallbacks(this._documentSetChanged, documentSet.attributes)
      this.listenTo(this.state.documentSet, 'change', notify)
    }
    listenToDocumentSet()

    const listenToDocument = () => {
      const notify = (document) => runCallbacks(this._documentChanged, document ? document.attributes : null)

      let lastDocument = null
      const setDocument = (document) => {
        if (lastDocument) this.stopListening(lastDocument)
        lastDocument = document
        if (lastDocument) this.listenTo(lastDocument, 'change', notify)
      }

      this.listenTo(this.state, 'change:document', (_, document) => {
        setDocument(document)
        notify(document)
      })

      setDocument(this.state.get('document'))
    }
    listenToDocument()
  }

  // Remove all pointers to other objects
  destroy() {
    this.stopListening()
    this.state = this.globalActions = this._documentSetChanged = this._documentChanged = null
  }

  onDocumentSetChanged(f) {
    this._documentSetChanged.push(f)
  }

  onDocumentChanged(f) {
    this._documentChanged.push(f)
  }

  requestDocumentSet(f) {
    runCallbacks(this._documentSetChanged, this.state.documentSet.attributes)
  }

  requestDocument(f) {
    const document = this.state.get('document')
    runCallbacks(this._documentChanged, document ? document.attributes : null)
  }

  openMetadataSchemaEditor() {
    this.globalActions.openMetadataSchemaEditor()
  }

  saveDocumentMetadata(documentId, metadata) {
    const document = this.state.get('document')
    if (document === null || document.id != documentId) {
      throw new Error('Tried to save document metadata on a document we are not viewing')
    }
    document.save({ metadata: metadata }, { patch: true })
  }
}
