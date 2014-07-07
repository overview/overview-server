define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.apps.Job')

  class JobApp extends Backbone.View
    template: _.template("""
      <div class="job-app">
        <h3><%- t('heading') %></h3>
        <p class="description"><%- t('description') %></p>
        <progress value="<%- progress.fraction %>"></progress>
        <p class="status"><%- progress.description %></p>
      </div>
    """)

    initialize: (options) ->
      throw 'Must pass options.viz, a Job Viz' if !options.viz?

      @viz = options.viz

      @render()

      @$progress = @$('progress')
      @$description = @$('p.status')

      @listenTo(@viz, 'change:progress', @_update)

    _update: (__, progress) ->
      @$progress.prop('value', progress?.fraction || 0)
      @$description.text(progress?.description || '')

    render: ->
      html = @template
        t: t
        progress:
          fraction: @viz.attributes.progress?.fraction || 0
          status: @viz.attributes.progress?.description || ''
      @$el.html(html)

      this
