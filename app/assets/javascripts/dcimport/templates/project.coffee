define [ 'underscore' ], (_) ->
  # Parameters:
  # * url: the URL to submit to
  # * project: DC project JSON, with "id" and "title".
  # * credentials: object with "email" and "password".
  # * window.defaultLanguageCode
  template = _.template("""
    <li>
      <form method="post" class="form-inline" action="<%- url %>">
        <%= window.csrfTokenHtml %>
        <input type="hidden" name="title" value="<%- project.title %>" />
        <input type="hidden" name="query" value="projectid:<%- project.id %>" />
        <input type="hidden" name="documentcloud_username" value="<%- credentials.email %>" />
        <input type="hidden" name="documentcloud_password" value="<%- credentials.password %>" />
        <input type="hidden" name="split_documents" value="false" />
        <input type="hidden" name="lang" value="<%- window.defaultLanguageCode %>" />
        <button class="btn btn-primary"><%- i18n('views.DocumentSet._documentSet.action_import') %></button>
      </form>
      <h3><%- project.title %></h3>
      <h4>
        <span class="document-count"><%- i18n('views.DocumentSet._documentSet.nDocuments', project.document_count || (project.document_ids && project.document_ids.length)) %></span>
      </h4>
    </li>""")
