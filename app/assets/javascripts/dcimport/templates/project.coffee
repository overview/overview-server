define [ 'underscore' ], (_) ->
  # Parameters:
  # * url: the URL to submit to
  # * project: DC project JSON, with "id" and "title".
  # * credentials: object with "email" and "password".
  template = _.template("""
    <li>
      <form method="post" class="form-inline" action="<%- url %>">
        <input type="hidden" name="title" value="<%- project.title %>" />
        <input type="hidden" name="project_id" value="<%- project.id %>" />
        <input type="hidden" name="documentcloud_username" value="<%- credentials.email %>" />
        <input type="hidden" name="documentcloud_password" value="<%- credentials.password %>" />
        <input type="hidden" name="split_documents" value="false">
        <button class="btn">
          <i class="icon-download"></i> <%- i18n('views.DocumentSet._documentSet.action_import') %>
        </button>
      </form>
      <h3><%- project.title %></h3>
      <p class="status"><span class="document-count"><%- i18n('views.DocumentSet._documentSet.document_count', project.document_ids.length) %></span></p>
    </li>""")
