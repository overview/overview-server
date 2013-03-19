define [ 'jquery', 'underscore' ], ($, _) ->
  _.template("""
    <h2><%- i18n('views.DocumentSet._dcimport.legend') %></h2>
    <form class="form-horizontal" method="get" action="#">
      <% if (error) { %>
        <div class="control-group error">
          <div class="controls">
            <span class="help-block error"><%- i18n('views.DocumentSet._dcimport.error') %></span>
          </div>
        </div>
      <% } %>
      <fieldset class="control-group">
        <label class="control-label" for="dcimport-email"><%- i18n('views.DocumentSet._dcimport.labels.email') %></label>
        <div class="controls">
          <input type="email" name="dcimport_email" id="dcimport-email" required="required" placeholder="<%- i18n('views.DocumentSet._dcimport.placeholders.email') %>" />
        </div>
      </fieldset>
      <fieldset class="control-group">
        <label class="control-label" for="dcimport-password"><%- i18n('views.DocumentSet._dcimport.labels.password') %></label>
        <div class="controls">
          <input type="password" name="dcimport_password" id="dcimport-password" required="required" />
          <p class="help-block"><%- i18n('views.DocumentSet._dcimport.explanation#{if $.support.cors then '' else '_no_cors'}') %></p>
        </div>
      </fieldset>
      <fieldset class="control-group form-actions">
        <input type="submit" class="btn btn-primary" value="<%- i18n('views.DocumentSet._dcimport.submit') %>" />
      </fieldset>
    </form>""")
