define ['underscore'], (_) ->
  template = _.template("""
    <div id="import-options">
      <form method="get" class="form update">
        <fieldset class="control-group">
          <label class="control-label" for="set-lang"><%- i18n('views.DocumentSet._dcimport.labels.language') %></label>
          <div class="controls">
            <select name="lang" id="set-lang">
              <% _.each(window.supportedLanguages, function(lang) { %>
                <option
                  <%- lang.code == window.defaultLanguageCode && 'selected="selected"' || '' %>
                  value="<%- lang.code %>"
                  ><%- lang.name %></option>
              <% }) %>
            </select>
          </div>
        </fieldset>
        <fieldset class="control-group">
          <div class="controls">
            <label>
              <input type="checkbox" name="split_documents" value="false" />
              <%- i18n('views.DocumentSet._dcimport.labels.split_documents') %>
            </label>
          </div>
        </fieldset>
      </form>
    </div>""")

