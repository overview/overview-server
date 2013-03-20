define [ 'backbone', 'i18n' ], (Backbone, i18n) ->
  t = (key, args...) -> i18n("views.Document.show.#{key}", args...)

  Backbone.View.extend
    tagName: 'h3'

    initialize: ->
      @model.on('change:document', => @render())
      @render()

    render: ->
      document = @model.get('document')
      heading = if document?
        document.get('heading') || t('heading.empty')
      else
        ''
      @$el.text(heading)
