define [
  'underscore',
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm')

  class UploadCollectionView extends Backbone.View
    template: _.template("""
      <ul>
        <li class="empty-upload">
          <i class='icon-cloud-upload'></i>
          <div><%- t('drop_target') %></div>
          <div class='secondary-prompt'><%- t('minimum_files') %></div>
        </li>
      </ul>
    """)

    initialize: ->
      throw 'Must pass uploadViewClass, a Backbone.View class' if !@options.uploadViewClass?
      @_views = [] # keep track of views, so we can remove() them

      @listenTo(@collection, 'add', @_onAdd)

    remove: ->
      @_removeAll()
      super()

    _removeAll: ->
      @_views.forEach((v) -> v.remove()) # Ends up calling stopListening()

    _onAdd: (upload) ->
      if @_emptyUploadIsPresent
        @_emptyUploadIsPresent = false
        @_$emptyUpload.remove()

      view = new @options.uploadViewClass(model: upload)
      view.render()
      @_views.push(view)
      @_$ul?.append(view.el)

    _onReset: ->
      @_removeAll()
      @collection.each(@_onAdd, @)

    render: ->
      html = @template(t: t)
      @$el.html(html)
      @_$ul = @$('ul')
      @_$emptyUpload = @$('li.empty-upload')
      @_emptyUploadIsPresent = true
      @_onReset()
