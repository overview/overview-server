define [ 'backbone' ], (Backbone) ->
  Backbone.Model.extend
    defaults:
      split_documents: false
      supplied_stop_words: ''
      important_words: ''
      lang: ''
      name: ''
      tag_id: ''
      tree_title: ''

    initialize: (attributes, options) ->
      throw 'Must set options.defaultLanguageCode' if !options.defaultLanguageCode
      throw 'Must set options.supportedLanguages' if !options.supportedLanguages
      throw 'Can only set one of options.excludeOptions or options.onlyOptions' if options.excludeOptions? && options.onlyOptions?

      if options.excludeOptions?
        for key in options.excludeOptions
          if key of @attributes
            delete @attributes[key]
          else
            throw "Invalid excludeOptions value '#{key}'"

      if options.onlyOptions?
        o = {}
        for k in options.onlyOptions
          if k of @defaults
            o[k] = null
          else
            throw "Invalid onlyOptions value '#{k}'"

        for k, __ of @defaults
          delete @attributes[k] if k not of o

      @supportedLanguages = options.supportedLanguages
      @set('lang', options.defaultLanguageCode) if @attributes.lang?
