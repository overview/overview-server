define [ 'backbone' ], (Backbone) ->
  PREFIX = 'apps/DocumentDisplay/models/Preferences/' # obsolete - for backwards compat

  PREFERENCES =
    'text': { type: 'boolean', default: false }
    'sidebar': { type: 'boolean', default: false }
    'wrap': { type: 'boolean', default: true }

  # Helper methods for dealing with various types
  #
  # localStorage stores only strings. This sorts out the other types.
  storage =
    # Get something from local storage. Returns undefined, or the value
    get:
      'boolean': (key) ->
        item = localStorage.getItem(PREFIX + key)
        if item?
          item == 'true'

    # Set something in local storage.
    set:
      'boolean': (key, value) ->
        localStorage.setItem(PREFIX + key, value && 'true' || 'false')

  # Stores data for the document-display dropdown menu and options.
  #
  # Attributes:
  #
  # * `text`: a localStorage boolean indicating we're in text mode
  # * `wrap`: a localStorage boolean indicating text should wrap
  # * `sidebar`: a localStorage boolean indicating we should show a sidebar
  # * `documentUrl`: the URL for opening the current document in a new tab
  # * `rootFile`: original-file information about the current document
  #
  # You store `text`, `wrap` and `sidebar` in localStorage by calling `set()`.
  # Other than that, this object behaves like a regular Backbone.Model.
  class DocumentDisplayPreferences extends Backbone.Model
    initialize: ->
      attrs = {}
      for pref, options of PREFERENCES
        attrs[pref] = storage.get[options.type](pref)
      @set(attrs, fromInit: true)

    set: (attrs, options=null) ->
      if !options?.fromInit
        for pref, options of PREFERENCES
          storage.set[options.type](pref, attrs[pref]) if attrs[pref]?
      super(attrs, options)
