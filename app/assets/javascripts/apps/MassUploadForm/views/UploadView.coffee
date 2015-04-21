define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  STATUS_ICONS =
    waiting: 'icon icon-clock-o'
    uploading: 'icon icon-spinner icon-spin'
    uploaded: 'icon icon-check'

  Backbone.View.extend
    tagName: 'li'
    template: _.template('''
      <i class='<%- icon %>'></i><span class='filename'><%- model.id %></span>
    ''')

    initialize: ->
      @_initialRender()
      @listenTo(@model, 'change', @render)

    _initialRender: ->
      status = @_getStatus()
      icon = STATUS_ICONS[status]

      @$el.html(@template
        model: @model
        icon: icon
      ).attr(class: status)

      @$iconEl = @$('i')
      @_lastStatus = status

    render: ->
      status = @_getStatus()
      return if status == @_lastStatus
      icon = STATUS_ICONS[status]

      @$iconEl.attr(class: icon)
      @$el.attr(class: status)

    _getStatus: ->
      if @model.get('uploading')
        'uploading'
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
