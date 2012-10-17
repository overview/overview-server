window.dcimport ||= {}
window.dcimport.templates ||= {}

template = _.template("""
  <form class="form-horizontal" method="get" action="#">
    <legend><%- i18n('views.DocumentSet._dcimport.legend') %></legend>
    <% if (error) { %>
      <div class="control-group error">
        <div class="controls">
          <span class="help-block error"><%- i18n('views.DocumentSet._dcimport.error') %></span>
        </div>
      </div>
    <% } %>
    <div class="control-group">
      <label class="control-label" for="dcimport-email"><%- i18n('views.DocumentSet._dcimport.labels.email') %></label>
      <div class="controls">
        <input type="email" name="dcimport_email" id="dcimport-email" required="required" placeholder="<%- i18n('views.DocumentSet._dcimport.placeholders.email') %>" />
      </div>
    </div>
    <div class="control-group">
      <label class="control-label" for="dcimport-password"><%- i18n('views.DocumentSet._dcimport.labels.password') %></label>
      <div class="controls">
        <input type="password" name="dcimport_password" id="dcimport-password" required="required" />
        <span class="help-inline"><%- i18n('views.DocumentSet._dcimport.explanation#{if $.support.cors then '' else '_no_cors'}') %></span>
      </div>
    </div>
    <div class="control-group">
      <div class="controls">
        <input type="submit" class="btn btn-primary" value="<%- i18n('views.DocumentSet._dcimport.submit') %>" />
      </div>
    </div>
  </form>""")

window.dcimport.templates.login = (error) -> template({ error: error })
