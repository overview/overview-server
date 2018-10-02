define [ 'backbone' ], (Backbone) ->
  Backbone.Model.extend
    defaults:
      split_documents: false
      ocr: false,
      lang: ''
      name: ''
      metadata_json: '{}'

    initialize: (attributes, options) ->
      throw 'Must set options.defaultLanguageCode' if !options.defaultLanguageCode
      throw 'Must set options.supportedLanguages' if !options.supportedLanguages
      throw 'Must set options.onlyOptions, an Array of Strings' if !options.onlyOptions

      o = {}
      for k in options.onlyOptions
        if k of @defaults
          o[k] = null
        else
          throw "Invalid onlyOptions value '#{k}'"

      for k, __ of @defaults
        delete @attributes[k] if k not of o

      @supportedLanguages = options.supportedLanguages
      @documentSet = options.documentSet
      @set('lang', options.defaultLanguageCode) if @attributes.lang?
