define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentCloudImportJob.new.#{key}", args...)

  Backbone.View.extend({
    className: 'documentcloud-project'

    template: _.template("""
      <div class="title"><%- project.title %></div>
      <% if (project.description) { %>
        <div class="description"><%- project.description %></div>
      <% } %>
      <div class="document-count"><%- t('project.document_count', project.document_ids.length) %></div>
    """)

    initialize: ->
      @render()
      @model.on('change:status', => @render())

    render: ->
      @$el.empty()

      return if @model.get('status') != 'fetched'

      data = _.extend(
        { isPrivate: !!(@model.get('credentials')?.isComplete()) },
        @model.get('project').attributes
      )

      html = @template({ t: t, project: data })
      @$el.html(html)

      this
  })
