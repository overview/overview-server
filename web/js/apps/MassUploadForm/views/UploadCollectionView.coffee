define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm')

  STATUS_ICONS =
    waiting: 'icon icon-clock-o'
    uploading: 'icon icon-spinner icon-spin'
    uploaded: 'icon icon-check'
    skipped: 'icon icon-check'

  upload_to_status = (upload) ->
    if upload.get('uploading')
      'uploading'
    else if upload.get('skippedBecauseAlreadyInDocumentSet')
      'skipped'
    else if upload.isFullyUploaded()
      'uploaded'
    else
      'waiting'

  class UploadCollectionView extends Backbone.View
    templates:
      main: _.template("""
        <ul></ul>
        <div class="empty-upload">
          <i class='icon icon-cloud-upload'></i>
          <div><%- t('drop_target') %></div>
        </div>
      """)
      li: _.template('<li class="<%= status %>"><td><i class="<%= icon_class %>"></i></td><td><span class="filename"><%- filename %></span><span class="message"></span></li>')

    initialize: ->
      @_idToIndex = {}

      @listenTo(@collection, 'add-batch', @_onAddBatch)
      @listenTo(@collection, 'change', @_onChange)
      @listenTo(@collection, 'reset', @_onReset)

    _onAddBatch: (uploads) ->
      return if !uploads.length

      if @_emptyUploadIsPresent
        @_$emptyUpload.remove()
        @_emptyUploadIsPresent = false

      htmls = []

      index = @_ul.childNodes.length
      for upload in uploads
        status = upload_to_status(upload)
        icon_class = STATUS_ICONS[status]
        htmls.push(@templates.li(icon_class: icon_class, status: status, filename: upload.id))
        @_idToIndex[upload.id] = index
        index += 1

      $(@_ul)
        .append(htmls.join(''))
        .height(index * @_ul.childNodes[0].clientHeight)

      @_liHeight = @_ul.childNodes[0].clientHeight

    _onChange: (upload) ->
      index = @_idToIndex[upload.id]

      return if !index?

      # Update the row
      status = upload_to_status(upload)
      icon_class = STATUS_ICONS[status]

      li = @_ul.childNodes[index]
      li.className = status

      li.querySelector('i').className = icon_class

      if upload.get('skippedBecauseAlreadyInDocumentSet')
        li.querySelector('.message').textContent = t('skipped')

      # Scroll a bit past the current row
      #
      # We debounce, because .offsetTop is expensive. Only request an animation
      # frame if we haven't requested one yet.
      if !@_currentIndex?
        window.requestAnimationFrame =>
          @el.scrollTop = @_liHeight * (@_currentIndex + 3) - @el.clientHeight
          @_currentIndex = null
      @_currentIndex = index

    _onReset: ->
      @_idToIndex = {}
      $(@_ul)
        .empty()
        .height(0)
        .after(@_$emptyUpload)
      @_emptyUploadIsPresent = true
      @_onAddBatch(@collection.models)

    render: ->
      html = @templates.main(t: t)
      @$el.html(html)
      @_ul = @$('ul').get(0)
      @_$emptyUpload = @$('.empty-upload')
      @_emptyUploadIsPresent = true
      @_onReset()
