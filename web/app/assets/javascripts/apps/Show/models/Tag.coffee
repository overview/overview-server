define [
  'underscore'
  'backbone'
  './color_table'
  'tinycolor'
], (_, Backbone, ColorTable, tinycolor) ->
  # A Tag.
  #
  # You can tag/untag documents before the Tag is saved, thanks to its
  # `whenExists()` method.
  class Tag extends Backbone.Model
    defaults:
      name: ''
      color: null

    initialize: (attributes, options) ->
      if !attributes.color
        @set(color: new ColorTable().get(attributes.name))

    # Returns "tag tag-light" or "tag tag-dark". The "tag-light" means the
    # text on the tag should be black; "tag-dark" means the text should be
    # white.
    getClass: ->
      c = tinycolor.mostReadable(@get('color'), ['white', 'black']).toName()
      switch c
        when 'white' then 'tag tag-dark'
        else 'tag tag-light'

    # Returns "background-color: [xxx]". When creating an HTML element, use
    # this in conjunction with getClass().
    getStyle: -> "background-color: #{@get('color')}"

    # Calls the given callback after the tag has an ID.
    #
    # When don't know whether the tag has been *deleted* at this point. We only
    # know that it has been *created*. Presumably, the callback will rely on
    # the Tag's ID.
    whenExists: (callback) ->
      if @id
        callback()
      else
        @once('sync', => @whenExists(callback))

    # Sends a POST /documentsets/:id/tags/:id/add with the given query string
    #
    # For instance: `tag.addToDocumentsOnServer(nodes: '3,4')`
    #
    # Triggers `documents-changed(tag)` on success.
    #
    # RACE WARNING: the client waits until the tag exists before sending a
    # request to the server. This does not block future tagging operations
    # from occurring. So if you do something like this:
    #
    # 1. Create tag 'foo', sending a request to the server
    # 2. Tag documents tagged 'bar' with 'foo' (waits for response to 1)
    # 3. Tag documents 6-15 as 'bar' (queues an AJAX request)
    # 4. Receive response from 1
    #
    # ... The request for 3 will be sent _before_ the request for 2, and the
    # client and server states will diverge.
    #
    # The solution to this race is to use TransactionQueue; but as of 2015-04-14
    # the only method on TransactionQueue is `.ajax()`, which is not enough. We
    # need to be able to run arbitrary async code in a queue.
    addToDocumentsOnServer: (documentQueryParams) ->
      @whenExists =>
        Backbone.ajax
          type: 'POST'
          url: "#{_.result(@, 'url')}/add"
          data: documentQueryParams
          debugInfo: 'Tag.addToDocumentsOnServer'
          success: => @trigger('documents-changed', @)

    # Sends a POST /documentsets/:id/tags/:id/remove for the given documents
    #
    # Triggers `documents-changed(tag)` on success.
    #
    # Beware the RACE WARNING described in addToDocumentsOnServer().
    removeFromDocumentsOnServer: (documentQueryParams) ->
      @whenExists =>
        Backbone.ajax
          type: 'POST'
          url: "#{_.result(@, 'url')}/remove"
          data: documentQueryParams
          debugInfo: 'Tag.removeFromDocumentsOnServer'
          success: => @trigger('documents-changed', @)
