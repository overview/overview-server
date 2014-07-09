define [
  'jquery'
  'rsvp'
], ($, RSVP) ->
  class Document
    constructor: (options) ->
      @id = options.id || 0
      @title = options.title || ''
      @description = options.description || ''
      @url = options.url || null
      @urlProperties = options.urlProperties
      @heading = @title || @description

    equals: (rhs) -> @id == rhs.id

    getText: ->
      @text ||= RSVP.resolve($.ajax(
        url: "/documents/#{@id}.txt"
        dataType: 'text'
      ))
