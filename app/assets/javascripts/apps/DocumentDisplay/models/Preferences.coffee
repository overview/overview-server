define [ 'backbone' ], (Backbone) ->
  PREFIX = 'apps/DocumentDisplay/models/Preferences/'
  PREFERENCES = {
    'iframe': { type: 'boolean', default: false }
    'sidebar': { type: 'boolean', default: false }
    'wrap': { type: 'boolean', default: true }
  }

  # Helper methods for dealing with various types
  #
  # localStorage stores only strings. This sorts out the other types.
  storage = {
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
  }

  Backbone.Model.extend
    getPreference: (key) ->
      throw "Invalid key '#{key}' in get" if !PREFERENCES[key]
      pref = PREFERENCES[key]
      item = storage.get[pref.type](key)
      if item?
        item
      else
        pref.default

    setPreference: (key, value) ->
      throw "Invalid key '#{key}' in set" if !PREFERENCES[key]
      pref = PREFERENCES[key]
      storage.set[pref.type](key, value)
      realValue = storage.get[pref.type](key)
      @set(key, realValue) # calls @trigger()
