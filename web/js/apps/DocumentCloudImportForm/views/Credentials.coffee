define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentCloudImportJob.new')

  statusTemplates = {
    unknown: -> ''

    error: _.template("""
      <div class="documentcloud-login-failed">
        <p class="preamble"><%- t("credentials_preamble") %></p>
        <div class="prompt">
          <div class="form-group">
            <label for="documentcloud-email"><%- t("email.label") %></label>
            <input
              type="email"
              id="documentcloud-email"
              name="documentcloud_username"
              class="form-control"
              placeholder="<%- t("email.placeholder") %>"
              />
          </div>
          <div class="form-group">
            <label for="documentcloud-password"><%- t("password.label") %></label>
            <input
              type="password"
              id="documentcloud-password"
              name="documentcloud_password"
              class="form-control"
              />
          </div>
          <button type="submit" class="btn btn-primary"><%- t("submit_credentials.label") %></button>
          <p class="help-block"><%- t("submit_credentials.preamble." + ($.support.cors ? "cors" : "no_cors")) %></p>
        </div>
      </div>
    """)

    fetched: _.template("""
      <p class="fetched"><%- t("fetched") %></p>
      <input type="hidden" name="documentcloud_username" value="<%- credentials.get('email') %>" />
      <input type="hidden" name="documentcloud_password" value="<%- credentials.get('password') %>" />
    """)

    fetching: _.template("""
      <div class="loading"><progress></progress><%- t("fetching") %></div>
    """)
  }

  # Calls model.set('credentials', ...), based on model.get('status')
  Backbone.View.extend
    className: 'credentials'

    initialize: ->
      @model.on('change:status', => @render())
      @render()

    render: ->
      status = @model.get('status')
      template = statusTemplates[status]
      html = template(_.extend({ t: t }, @model.attributes))
      @$el.html(html)

      this
