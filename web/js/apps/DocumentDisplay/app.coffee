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
        preferences: @preferences.attributes,
        transactionQueue: Backbone, # HACK -- "Backbone.ajax" is actually the same as transactionQueue.ajax
      },
    })

    @listenTo(@preferences, 'change', (preferences) => @appView.set({ preferences: preferences.attributes }))

  # Show a new document.
  #
  # The document may be:
  #
  # * A JSON object with id and url properties (in our database)
  # * A JSON object with an id property (in our database)
  # * null
  setDocument: (json) ->
    document = DocumentWrapper.wrap(json, @urlPropertiesExtractor)

    @appView.set({ document: document })

  # If there's a PDF window open, tell it to begin creating a Note
  beginCreatePdfNote: ->
    @appView?.beginCreatePdfNote()

  # Highlight a new search phrase
  #
  # The search phrase may be a String or <tt>null</tt>.
  setSearch: (q) ->
    @appView.set({ highlightQ: q })
