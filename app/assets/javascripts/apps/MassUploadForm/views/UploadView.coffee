define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  STATUS_ICONS =
    waiting: 'icon-ellipsis-horizontal'
    uploading: 'icon-spinner icon-spin'
    uploaded: 'icon-check'

  Backbone.View.extend
    tagName: 'li'
    template: _.template('''
      <span class='filename'><%- model.id %></span><i class='<%- icon %>'></i>
      ''')

    initialize: ->
      @listenTo(@model, 'change', @render)

    render: ->
      status = @_getStatus()
      icon = STATUS_ICONS[status]

      @$el.html(@template
        model: @model
        icon: icon
      ).attr('class', status)

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
