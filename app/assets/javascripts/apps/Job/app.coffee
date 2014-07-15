define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.apps.Job')

  class JobApp extends Backbone.View
    template: _.template("""
      <div class="job-app <%- (progress.state || '').toLowerCase() %>">
        <h3><%- progress.state == 'ERROR' && t('heading.error') || t('heading') %></h3>
        <p class="description"><%- t('description') %></p>
        <progress value="<%- progress.fraction || 0 %>"></progress>
        <p class="status"><%- progress.description || '' %></p>
      </div>
    """)

    initialize: (options) ->
      throw 'Must pass options.viz, a Job Viz' if !options.viz?

      @viz = options.viz

      @render()

      @$h3 = @$('h3')
      @$progress = @$('progress')
      @$description = @$('p.status')

      @listenTo(@viz, 'change:progress', @_update)

    _update: (__, progress) ->
      progress ?= {}
      @$el.children()[0].className = "job-app #{(progress.state || '').toLowerCase()}"
      @$h3.text(progress.state == 'ERROR' && t('heading.error') || t('heading'))
      @$progress.prop('value', progress.fraction || 0)
      @$description.text(progress.description || '')

    render: ->
      progress = @viz.attributes.progress || {}

      html = @template
        t: t
        progress: progress
      @$el.html(html)

      this
