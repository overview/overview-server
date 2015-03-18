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
