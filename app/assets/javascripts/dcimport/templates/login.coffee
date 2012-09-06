window.dcimport ||= {}
window.dcimport.templates ||= {}

m = {
  legend: 'Enter your DocumentCloud email and password',
  error: 'You entered an invalid username or password, or DocumentCloud is unavailable. Please try again.'
  labels: {
    email: 'Email',
    password: 'Password',
  },
  placeholders: {
    email: 'user',
  }
  submit: 'Log in to DocumentCloud',
  explanation: 'Your browser will store this data in memory. It will not be sent to the Overview servers until you decide to import a project. Overview servers will use it to import your project, and then they will delete it.',
}

template = _.template("""
  <form class="form-horizontal" method="get" action="#">
    <legend><%- m.legend %></legend>
    <% if (error) { %>
      <div class="control-group error">
        <div class="controls">
          <span class="help-block error"><%- m.error %></span>
        </div>
      </div>
    <% } %>
    <div class="control-group">
      <label class="control-label" for="dcimport-email"><%- m.labels.email %></label>
      <div class="controls">
        <input type="email" name="dcimport_email" id="dcimport-email" required="required" placeholder="<%- m.placeholders.email %>" />
      </div>
    </div>
    <div class="control-group">
      <label class="control-label" for="dcimport-password"><%- m.labels.password %></label>
      <div class="controls">
        <input type="password" name="dcimport_password" id="dcimport-password" required="required" />
        <span class="help-inline"><%- m.explanation %></span>
      </div>
    </div>
    <div class="control-group">
      <div class="controls">
        <input type="submit" class="btn btn-primary" value="<%- m.submit %>" />
      </div>
    </div>
  </form>""")

window.dcimport.templates.login = (error) -> template({ m: m, error: error })
