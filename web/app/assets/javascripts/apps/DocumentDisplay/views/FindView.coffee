define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.Document.show.FindView')

  # Shows the current search and lets you flip through results
  class FindView extends Backbone.View
    className: 'find'

    template: _.template("""
      <div class="label"></div>
      <a href="#" class="previous-highlight" title="<%- t('previousHighlight') %>"><i class="icon overview-icon-chevron-left"></i></a>
      <a href="#" class="next-highlight" title="<%- t('nextHighlight') %>"><i class="icon overview-icon-chevron-right"></i></a>
    """)

    events:
      'click a.previous-highlight': '_onClickPreviousHighlight'
      'click a.next-highlight': '_onClickNextHighlight'

    initialize: (options) ->
      throw 'Must pass options.model, a TextDocument' if !@model?

      @listenTo(@model, 'change', @render)

      @render()

    render: ->
      @_initialRender() if !@ui?

      attrs = @model.attributes

      if !attrs.highlights? || !attrs.highlightsQuery || !attrs.highlightsIndex? || !attrs.text || attrs.highlightsError?
        @$el.addClass('hidden')
      else
        @$el.removeClass('hidden')
        @ui.label.text(t('label', attrs.highlightsQuery, attrs.highlightsIndex + 1, attrs.highlights.length))
        @ui.previous.toggleClass('disabled', attrs.highlightsIndex == 0)
        @ui.next.toggleClass('disabled', attrs.highlightsIndex >= attrs.highlights.length - 1)

      @

    _initialRender: ->
      html = @template(t: t)
      @$el.html(html)

      @ui =
        label: @$('.label')
        previous: @$('.previous-highlight')
        next: @$('.next-highlight')

    _onClickPreviousHighlight: (e) -> @_navigate(e, -1)
    _onClickNextHighlight: (e) -> @_navigate(e, 1)

    _navigate: (e, d) ->
      e.preventDefault()

      max = @model.get('highlights')?.length - 1
      requested = @model.get('highlightsIndex') + d

      if max? && requested?
        @model.set(highlightsIndex: Math.min(Math.max(0, @model.get('highlightsIndex') + d), max))
