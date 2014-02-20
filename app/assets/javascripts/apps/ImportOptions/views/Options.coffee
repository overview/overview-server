define [
  'underscore'
  'backbone'
  './TagIdInput'
  'i18n'
], (_, Backbone, TagIdInput, i18n) ->
  t = i18n.namespaced('views.DocumentSet.index.ImportOptions')

  # Presents an Options in a write-only manner.
  #
  # This is a view/controller mishmash. As the user clicks here, the model will
  # change. If the model changes, this view will not change.
  class OptionsView extends Backbone.View
    tagName: 'fieldset'
    className: 'import-options'

    events:
      'change [name=name]': '_onChangeName'
      'change [name=tree_title]': '_onChangeTreeTitle'
      'change [name=split_documents]': '_onChangeSplitDocuments'
      'change [name=lang]': '_onChangeLang'
      'change [name=supplied_stop_words]': '_onChangeSuppliedStopWords'
      'change [name=important_words]': '_onChangeImportantWords'

    template: _.template("""
      <% if ('tree_title' in options) { %>
        <div class="control-group">
          <label class="control-label" for="import-options-tree-title"><%- t('tree_title.label') %></label>
          <div class="controls">
            <input required="required" type="text" id="import-options-tree-title" name="tree_title" value="<%- options.tree_title %>" />
          </div>
        </div>
      <% } %>

      <% if ('name' in options) { %>
        <div class="control-group">
          <label class="control-label" for="import-options-name"><%- t('name.label') %></label>
          <div class="controls">
            <input required="required" type="text" id="import-options-name" name="name" value="<%- options.name %>" />
          </div>
        </div>
      <% } %>

      <% if ('tag_id' in options) { %>
        <div class="control-group">
          <label class="control-label"><%- t('tag_id.label') %></label>
          <div class="controls">
            <div class="tag-id"></div>
          </div>
        </div>
      <% } %>

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

      <% if ('important_words' in options) { %>
        <div class="control-group">
          <label class="control-label" for="import-options-important-words"><%- t('important_words.label') %> (<a href="http://overview.ap.org/blog/2014/01/use-important-and-ignored-words-to-tell-overview-how-to-file-your-documents/" target="_blank" title="<%- t('click_for_help') %>">?</a>)</label>
          <div class="controls">
            <textarea id="import-options-important-words" name="important_words"><%- options.important_words %></textarea>
            <p class="help-block"><%- t('important_words.help') %></p>
          </div>
        </div>
      <% } %>
    """)

    initialize: ->
      throw 'Must pass model, an Options model' if !@model

      @childViews = []
      @initialRender()

    remove: ->
      for v in @childViews
        v.remove()
      super()

    initialRender: ->
      html = @template({
        t: t
        supportedLanguages: @model.supportedLanguages
        options: @model.attributes
      })
      @$el.html(html)

      $tagId = @$('.tag-id')
      if $tagId.length
        childView = new TagIdInput
          model: @model
          el: $tagId.get(0)
          tagListUrl: @options.tagListUrl
        @childViews.push(childView)
        childView.render()

    _onChangeSplitDocuments: (e) ->
      @model.set('split_documents', Backbone.$(e.target).prop('checked'))

    _onChangeLang: (e) ->
      @model.set('lang', Backbone.$(e.target).val())

    _onChangeSuppliedStopWords: (e) ->
      @model.set('supplied_stop_words', Backbone.$(e.target).val())

    _onChangeName: (e) ->
      @model.set('name', Backbone.$(e.target).val())

    _onChangeTreeTitle: (e) ->
      @model.set('tree_title', Backbone.$(e.target).val())

    _onChangeImportantWords: (e) ->
      @model.set('important_words', Backbone.$(e.target).val())
