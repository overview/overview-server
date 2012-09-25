window.dcimport ||= {}
window.dcimport.templates ||= {}

r = {
  DocumentSetController: {
    create: () -> '/documentsets'
  }
}

template = _.template("""
  <li>
    <form method="post" class="form-inline" action="<%- r.DocumentSetController.create() %>">
      <input type="hidden" name="title" value="<%- project.title %>" />
      <input type="hidden" name="query" value="projectid:<%- project.id %>" />
      <input type="hidden" name="documentcloud_username" value="<%- credentials.email %>" />
      <input type="hidden" name="documentcloud_password" value="<%- credentials.password %>" />
      <button class="btn">
        <i class="icon-download"></i> <%- i18n('views.DocumentSet._documentSet.action_import') %>
      </button>
    </form>
    <h3><%- project.title %></h3>
    <p class="status"><span class="document-count"><%- i18n('views.DocumentSet._documentSet.document_count', project.document_ids.length) %></span></p>
  </li>""")

window.dcimport.templates._project = (project, credentials) -> template({ project: project, r: r, credentials: credentials })
