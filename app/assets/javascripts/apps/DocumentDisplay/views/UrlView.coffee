define [
  'backbone'
  'i18n'
], (Backbone, i18n) ->
  t = i18n.namespaced('views.Document.show.Url')

  class UrlView extends Backbone.View
    tagName: 'p'
    className: 'open-in-new-tab empty'

    initialize: ->
      @render()

    render: ->
      @$a = Backbone.$('<a target="_blank" href=""></a>').text(t('text'))
      @$el.append(@$a)
      @

    setUrl: (url) ->
      @$a.attr('href', url)
      @$el.toggleClass('empty', !url)
      @
