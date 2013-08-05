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
      <div class="control-group">
        <div class="controls">
          <label>
            <input type="checkbox" name="split_documents" <%= split_documents ? 'checked="checked"' : '' %> value="true" />
            <%- t('split_documents.label') %>
          </label>
        </div>
      </div>

      <div class="control-group">
        <label class="control-label" for="import-options-lang"><%- t('lang.label') %></label>
        <div class="controls">
          <select id="import-options-lang" name="lang">
            <% _.each(supportedLanguages, function(language) { %>
              <option value="<%- language.code %>" <%= lang == language.code ? 'selected="selected"' : '' %>>
                <%- language.name %>
              </option>
            <% }); %>
          </select>
        </div>
      </div>

      <div class="control-group">
        <label class="control-label" for="import-options-supplied-stop-words"><%- t('supplied_stop_words.label') %></label>
        <div class="controls">
          <textarea id="import-options-supplied-stop-words" name="supplied_stop_words"><%- supplied_stop_words %></textarea>
          <p class="help-block"><%- t('supplied_stop_words.help') %></p>
        </div>
      </div>
    """)

    initialize: ->
      throw 'Must pass model, an Options model' if !@model

      @initialRender()

    initialRender: ->
      html = @template(_.extend({ t: t, supportedLanguages: @model.supportedLanguages }, @model.attributes))
      @$el.html(html)

    _onChangeSplitDocuments: (e) ->
      @model.set('split_documents', Backbone.$(e.target).prop('checked'))

    _onChangeLang: (e) ->
      @model.set('lang', Backbone.$(e.target).val())

    _onChangeSuppliedStopWords: (e) ->
      @model.set('supplied_stop_words', Backbone.$(e.target).val())
