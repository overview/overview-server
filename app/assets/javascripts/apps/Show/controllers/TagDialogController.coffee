define [
  'jquery'
  'underscore'
  'backbone'
  '../views/TagList'
  'i18n'
], ($, _, Backbone, TagListView, i18n) ->
  t = i18n.namespaced('views.Tree.show.tag_list')

  template = _.template("""
    <div class="modal fade">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
            <h4 class="modal-title"><%- t('header') %></h3>
          </div>
          <div class="modal-body"></div>
          <div class="modal-footer">
            <a href="#" class="btn" data-dismiss="modal">Close</a>
          </div>
        </div>
      </div>
    </div>
  """)

  # Opens a dialog showing the tags in the tag store.
  #
  # This dialog allows edits. It will be closed when the user clicks "close".
  #
  # Usage:
  #
  #   new TagDialogController(tags: tags, state: state)
  #
  # Test usage:
  #
  #   controller = new TagDialogController
  #     tags: mockTags
  #     view: new Backbone.View
  #     state: new Backbone.Model
  class TagDialogController
    constructor: (@options) ->
      throw 'Must set options.tags, a Tags' if !@options.tags?
      throw 'Must set options.state, a State' if !@options.state?

      @tags = @options.tags
      @state = @options.state

      @_createView()
      @_attachView()

    _createView: ->
      # Create a view, unless one was supplied in the constructor (for testing)
      @view = if @options.view?
        @options.view
      else
        new TagListView
          collection: @tags
          exportUrl: "#{@tags.url}.csv" # TODO routing in JS

    _attachView: ->
      view = @view

      @listenTo view, 'add', (attrs) =>
        tag = @tags.create(attrs)

      @listenTo view, 'update', (tag, attrs) ->
        tag.save(attrs)

      @listenTo view, 'remove', (tag) ->
        if @state.get('taglikeCid') == tag.cid
          @state.set(taglikeCid: null)
        if (params = @state.get('documentListParams'))? && params.type == 'tag' && params.tag == tag
          @state.resetDocumentListParams().all()
        tag.destroy()

      @$dialog = $dialog = $(template({ t: t }))
      $dialog.find('.modal-body').append(view.el)

      $dialog
        .appendTo('body')
        .modal()
        .on 'hidden.bs.modal', =>
          view.remove()
          view = undefined
          $dialog.remove()
          @$dialog = undefined
          @stopListening()

      # Refresh tag counts
      url = if (viz = @state.get('viz'))?
        @tags.url.replace(/\/tags$/, "/trees/#{@state.get('viz').get('id')}/tags")
      else
        @tags.url

      @tags.fetch
        url: url
        success: ->
          view?.render() # tag counts have changed; render that

    _.extend(@::, Backbone.Events)
