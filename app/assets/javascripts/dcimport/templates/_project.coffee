window.dcimport ||= {}
window.dcimport.templates ||= {}

r = {
  DocumentSetController: {
    create: () -> '/documentsets'
  }
}

m = {
  import: 'Import',
  document_count: (n) -> if n == 1 then "one document" else "#{n} documents",
}

template = _.template("""
  <li>
    <form method="post" class="form-inline" action="<%- r.DocumentSetController.create() %>">
      <input type="hidden" name="title" value="<%- project.title %>" />
      <input type="hidden" name="query" value="projectid:<%- project.id %>" />
      <input type="hidden" name="documentcloud_username" value="<%- credentials.username %>" />
      <input type="hidden" name="documentcloud_password" value="<%- credentials.password %>" />
      <button class="btn">
        <i class="icon-download"></i> <%- m.import %>
      </button>
    </form>
    <h2><%- project.title %></h2>
    <p class="status"><span class="document-count"><%- m.document_count(project.document_ids.length) %></span></p>
  </li>""")

window.dcimport.templates._project = (project, credentials) -> template({ project: project, m: m, r: r, credentials: credentials })
