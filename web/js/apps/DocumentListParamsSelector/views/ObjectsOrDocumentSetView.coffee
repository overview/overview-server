define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListParamsSelector.ObjectsOrDocumentSetView')

  # Lets the user select whether to show documents in the entire docset or
  # refined by a set of Objects with a custom title.
  #
  # DEPRECATED: this is where nodes factor in, too.
  class ObjectsOrDocumentSetView extends Backbone.View
    template: _.template('''
      <span class="title"><%- title %></span>
      <% if (canNix) { %>
        <a href="#" class="nix">&times;</a>
      <% } %>
    ''')

    initialize: (options) ->
      throw new Error('Must set options.model, a Backbone.Model of a DocumentListParams') if 'model' not of options
      throw new Error('Must set options.state, an object with refineDocumentListParams') if 'state' not of options

      @state = options.state
      @render()

      @listenTo(@model, 'change:title', @render)

    events:
      'click a.nix': '_onClickNix'

    render: ->
      title = @model.get('title')
      if title?
        canNix = true
      else
        canNix = false
        title = t('all')

      title = title.replace('%s', t('term'))

      @$el.html(@template(title: title, canNix: canNix))
      @

    _onClickNix: (e) ->
      e.preventDefault()
      @state.refineDocumentListParams(objects: null)
