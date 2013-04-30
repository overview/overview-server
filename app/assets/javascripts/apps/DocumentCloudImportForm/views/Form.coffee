define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentCloudImportJob.new.#{key}", args...)

  Backbone.View.extend({
    className: 'import-form'

    template: _.template("""
      <p class="preamble"><%- t("form_preamble") %></p>
      <div class="prompt form-horizontal">
        <div class="control-group">
          <label class="control-label" for="documentcloud-title"><%- t("title.label") %></label>
          <div class="controls">
            <input
              type="text"
              id="documentcloud-title"
              name="title"
              />
          </div>
        </div>
        <div class="control-group">
          <div class="controls">
            <label>
              <input type="checkbox" name="split_documents" />
              <%- t("split_documents.label") %>
            </label>
          </div>
        </div>
        <div class="control-group">
          <div class="controls">
            <button type="submit" class="btn"><%- t("submit.label") %></button>
          </div>
        </div>
      </div>
    """)

    initialize: ->
      @_renderInitial()

      @model.on('change:status', => @render())
      @render()

    render: ->
      if @model.get('status') == 'fetched'
        project = @model.get('project')
        @$('input[name=title]')
          .attr('placeholder', t("title.placeholder"))
          .val(t('title.value', project.get('title')))
        @$el.show()
      else
        @$el.hide()

    _renderInitial: ->
      html = @template({ t: t })
      @$el.html(html)
  })
