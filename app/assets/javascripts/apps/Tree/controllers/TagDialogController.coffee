define [
  'jquery'
  'underscore'
  'backbone'
  '../models/DocumentListParams'
  '../views/TagList'
  'i18n'
], ($, _, Backbone, DocumentListParams, TagListView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.tag_list')

  template = _.template("""
    <div class="modal fade">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h3><%- t('header') %></h3>
      </div>
      <div class="modal-body"></div>
      <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
      </div>
    </div>
  """)

  base_url = () ->
    if match = /documentsets\/(\d+)\/.*$/.exec(window.location.pathname)
      document_set_id = +match[1]
      "/documentsets/#{document_set_id}"
        

  # Opens a dialog showing the tags in the tag store.
  #
  # This dialog allows edits. It will be closed when the user clicks "close".
  #
  # Usage:
  #
  #   new TagDialogController(cache: cache, tagStoreProxy: tagStoreProxy, state: state)
  #
  # Test usage:
  #
  #   controller = new TagDialogController
  #     cache: mockCache
  #     tagStore: mockTagStore
  #     view: new Backbone.View
  #     state: new Backbone.Model
  class TagDialogController
    constructor: (@options) ->
      throw 'Must set options.cache, a Cache' if !@options.cache?
      throw 'Must set options.tagStoreProxy, a TagStoreProxy' if !@options.tagStoreProxy?
      throw 'Must set options.state, a State' if !@options.state?

      @state = @options.state

      @_createView()
      @_attachView()


    _createView: ->
      # Create a view, unless one was supplied in the constructor (for testing)
      @view = if @options.view?
        @options.view
      else
        new TagListView
          collection: @options.tagStoreProxy.collection,
          exportUrl: base_url() + "/tags.csv" # TODO routing in JS

    _attachView: ->
      cache = @options.cache
      tagStoreProxy = @options.tagStoreProxy

      view = @view

      @listenTo view, 'add', (attrs) ->
        tag = cache.add_tag(attrs)

        cache.create_tag(tag, {
          beforeReceive: tagStoreProxy.setChangeOptions({ interacting: true })
        }).done ->
          tagStoreProxy.setChangeOptions({})

      @listenTo view, 'update', (model, attrs) ->
        tag = tagStoreProxy.unmap(model)
        tagStoreProxy.setChangeOptions({ interacting: true })
        cache.update_tag(tag, attrs)
        tagStoreProxy.setChangeOptions({})

      @listenTo view, 'remove', (model) ->
        tag = tagStoreProxy.unmap(model)
        if @state.get('taglike')?.tagId == tag.id
          @state.set(taglike: null)
        if (params = @state.get('documentListParams'))? && params.type == 'tag' && params.tagId == tag.id
          @state.setDocumentListParams(DocumentListParams.all())
        cache.delete_tag(tag)

      @$dialog = $dialog = $(template({ t: t }))
      $dialog.find('.modal-body').append(view.el)

      $dialog
        .appendTo('body')
        .modal()
        .on 'hidden', =>
          view.remove()
          $dialog.remove()
          @$dialog = undefined
          @stopListening()

      # Refresh tag counts
      cache.transaction_queue.queue ->
        $.getJSON(base_url() + "/tags.json") # TODO routing in JS
          .done (json) ->
            # The fresh data from the server will only be set in the proxy. It
            # won't be set in the underlying tag store.
            #
            # TODO remove proxying altogether and just use a Backbone.Collection.
            tagStoreProxy.collection.set(json?.tags || [])

    _.extend(@::, Backbone.Events)
