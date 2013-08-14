define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.index.ImportOptions.#{key}", args...)

  # Presents an Options in a write-only manner.
  #
  # This is a view/controller mishmash. As the user clicks here, the model will
  # change. If the model changes, this view will not change.
  Backbone.View.extend
    tagName: 'fieldset'
    className: 'import-options'

    events:
      'change [name=split_documents]': '_onChangeSplitDocuments'
      'change [name=lang]': '_onChangeLang'
      'change [name=supplied_stop_words]': '_onChangeSuppliedStopWords'

    template: _.template("""
      <% if ('split_documents' in options) { %>
        <div class="control-group">
          <div class="controls">
            <label>
              <input type="checkbox" name="split_documents" <%= options.split_documents ? 'checked="checked"' : '' %> value="true" />
              <%- t('split_documents.label') %> (<a href="http://overview.ap.org/blog/2013/04/dealing-with-massive-pdfs-by-splitting-them-into-pages/" target="_blank" title="<%- t('click_for_help') %>">?</a>)
            </label>
          </div>
        </div>
      <% } %>

      <% if ('lang' in options) { %>
        <div class="control-group">
          <label class="control-label" for="import-options-lang"><%- t('lang.label') %></label>
          <div class="controls">
            <select id="import-options-lang" name="lang">
              <% _.each(supportedLanguages, function(language) { %>
                <option value="<%- language.code %>" <%= options.lang == language.code ? 'selected="selected"' : '' %>>
                  <%- language.name %>
                </option>
              <% }); %>
            </select>
          </div>
        </div>
      <% } %>

      <% if ('supplied_stop_words' in options) { %>
        <div class="control-group">
          <label class="control-label" for="import-options-supplied-stop-words"><%- t('supplied_stop_words.label') %> (<a href="http://overview.ap.org/blog/2013/08/overview-not-clustering-the-way-youd-like-try-ignoring-words/" target="_blank" title="<%- t('click_for_help') %>">?</a>)</label>
          <div class="controls">
            <textarea id="import-options-supplied-stop-words" name="supplied_stop_words"><%- options.supplied_stop_words %></textarea>
            <p class="help-block"><%- t('supplied_stop_words.help') %></p>
          </div>
        </div>
      <% } %>
    """)

    initialize: ->
      throw 'Must pass model, an Options model' if !@model

      @initialRender()

    initialRender: ->
      html = @template({
        t: t
        supportedLanguages: @model.supportedLanguages
        options: @model.attributes
      })
      @$el.html(html)

    _onChangeSplitDocuments: (e) ->
      @model.set('split_documents', Backbone.$(e.target).prop('checked'))

    _onChangeLang: (e) ->
      @model.set('lang', Backbone.$(e.target).val())

    _onChangeSuppliedStopWords: (e) ->
      @model.set('supplied_stop_words', Backbone.$(e.target).val())
