import Backbone from 'backbone'
import AppView from './views/AppView'
import DocumentWrapper from './models/DocumentWrapper'
import UrlPropertiesExtractor from './models/UrlPropertiesExtractor'

export default class App extends Backbone.View
  # Creates a new DocumentDisplay.
  #
  # Callers should access the "el" property to insert it into
  # the page. Then they can call setDocument() to show a document.
  initialize: (options) ->
    throw 'Must pass options.preferences, a DocumentDisplayPreferences' if !options.preferences

    @urlPropertiesExtractor = new UrlPropertiesExtractor(documentCloudUrl: window.documentCloudUrl)
    @preferences = options.preferences

    @appView = new AppView({
      target: @el,
      data: {
        document: null,
        highlightQ: null,
        preferences: @preferences.toJSON(),
        transactionQueue: Backbone, # HACK -- "Backbone.ajax" is actually the same as transactionQueue.ajax
      },
    })

    @appView.on('changePdfNotes', (ev) =>
      if @document?.id == ev.documentId
        @document.savePdfNotes(ev.pdfNotes)
    )

    @listenTo(@preferences, 'change', (preferences) => @appView.set({ preferences: preferences.toJSON() }))

  # Present a new document and stop presenting the old one.
  #
  # The document must be a Backbone.Model or `null`
  setDocument: (document) ->
    update = () =>
      json = document && DocumentWrapper.wrap(document.toJSON(), @urlPropertiesExtractor) || null
      @appView.set({ document: json })

    @stopListening(@document) if @document
    @document = document
    @listenTo(@document, 'change:pdfNotes', update)

    update()

  # If there's a PDF window open, tell it to begin creating a Note
  beginCreatePdfNote: ->
    @appView?.beginCreatePdfNote()

  goToPdfNote: (note) ->
    @appView?.goToPdfNote(note)

  # Highlight a new search phrase
  #
  # The search phrase may be a String or <tt>null</tt>.
  setSearch: (q) ->
    @appView.set({ highlightQ: q })
