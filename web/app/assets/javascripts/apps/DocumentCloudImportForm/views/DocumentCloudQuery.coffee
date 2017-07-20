define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentCloudImportJob.new')

  Backbone.View.extend({
    className: 'documentcloud-query'

    template: _.template("""
      <p class="preamble"><%- t("query.preamble") %></p>
      <div class="details">
        <h3><%- query.title %></h3>
        <% if (query.description) { %>
          <p class="description"><%- query.description %></p>
        <% } %>
        <p class="document-count"><%- t('query.document_count', query.document_count) %></p>
      </div>
    """)

    initialize: ->
      @render()
      @model.on('change:status', => @render())

    render: ->
      @$el.empty()

      return if @model.get('status') != 'fetched'

      data = _.extend(
        { isPrivate: !!(@model.get('credentials')?.isComplete()) },
        @model.get('query').attributes
      )

      html = @template({ t: t, query: data })
      @$el.html(html)

      this
  })
