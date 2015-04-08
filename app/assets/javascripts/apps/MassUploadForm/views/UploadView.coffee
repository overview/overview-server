define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  STATUS_ICONS =
    waiting: 'icon-time'
    uploading: 'icon-spinner icon-spin'
    uploaded: 'icon-ok'

  Backbone.View.extend
    tagName: 'li'
    template: _.template('''
      <i class='icon <%- icon %>'></i><span class='filename'><%- model.id %></span>
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
      ).attr('class', status)

      @$iconEl = @$('i')

    render: ->
      status = @_getStatus()
      icon = STATUS_ICONS[status]

      @$iconEl.attr('class', icon)
      @$el.attr('class', status)

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
