define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm.Upload')

  STATUS_ICONS =
    waiting: 'icon icon-clock-o'
    uploading: 'icon icon-spinner icon-spin'
    uploaded: 'icon icon-check'
    skipped: 'icon icon-check'

  Backbone.View.extend
    tagName: 'li'
    template: _.template('''
      <i></i><span class='filename'><%- filename %></span><span class="message"></span>
    ''')

    initialize: ->
      @_initialRender()
      @listenTo(@model, 'change', @render)

    _initialRender: ->
      @_lastStatus = null

      @$el.html(@template(filename: @model.id))

      @ui =
        icon: @$('i')
        message: @$('.message')

      @render()

    render: ->
      status = @_getStatus()
      return if status == @_lastStatus
      icon = STATUS_ICONS[status]

      message = if status == 'skipped' then t('skipped') else ''
      @ui.icon.attr(class: icon)
      @ui.message.text(message)
      @$el.attr(class: status)

    _getStatus: ->
      if @model.get('uploading')
        'uploading'
      else if @model.get('skippedBecauseAlreadyInDocumentSet')
        'skipped'
      else if @model.isFullyUploaded()
        'uploaded'
      else
        'waiting'

      # for later.
      #
      # if @upload.get('deleting')
      #   'deleting'
      # else if !@upload.get('file')? && !@upload.isFullyUploaded()
      #   'must-reselect'
      # else if @upload.get('error')
      #   'error'
