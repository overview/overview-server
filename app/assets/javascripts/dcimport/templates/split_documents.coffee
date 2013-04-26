define ['underscore'], (_) ->
  template = _.template("""
    <form method="post" class="update form-inline" action="#split-document">
      <input id="set-split-documents" type="checkbox" name="split-documents" value="false" />
      <label for="set-split-documents"><%- i18n('views.DocumentSet._dcimport.labels.split_documents') %></label>
    </form>""")        