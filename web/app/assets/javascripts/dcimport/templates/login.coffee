define [ 'jquery', 'underscore' ], ($, _) ->
  _.template("""
    <p class="preamble"><%= i18n('views.DocumentSet._dcimport.preamble_html') %></p>
    <form class="dclogin" method="get" action="#">
      <% if (error) { %>
        <div class="form-group error">
          <p class="help-block error "><%- i18n('views.DocumentSet._dcimport.error') %></p>
        </div>
      <% } %>
      <fieldset class="form-group">
        <label for="dcimport-email" class="control-label "><%- i18n('views.DocumentSet._dcimport.labels.email') %></label>
        <div class="">
          <input
            type="email"
            id="dcimport-email"
            name="dcimport_email"
            class="form-control "
            required="required"
            placeholder="<%- i18n('views.DocumentSet._dcimport.placeholders.email') %>"
            />
        </div>
      </fieldset>
      <fieldset class="form-group">
        <label class="control-label " for="dcimport-password"><%- i18n('views.DocumentSet._dcimport.labels.password') %></label>
        <div class="">
          <input
            type="password"
            id="dcimport-password"
            name="dcimport_password"
            class="form-control"
            required="required"
            />
          <p class="help-block"><%- i18n('views.DocumentSet._dcimport.explanation#{if $.support.cors then '' else '_no_cors'}') %></p>
        </div>
      </fieldset>
      <div class="form-group">
        <div class=" ">
          <button type="submit" class="btn btn-primary"><%- i18n('views.DocumentSet._dcimport.submit') %></button>
        </div>
      </div>
    </form>""")
