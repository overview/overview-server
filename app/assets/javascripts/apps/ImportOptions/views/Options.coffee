define [
  'underscore'
  'backbone'
  '../../DocumentMetadata/App'
  'i18n'
], (_, Backbone, DocumentMetadataApp, i18n) ->
  t = i18n.namespaced('views.DocumentSet.index.ImportOptions')

  # Presents an Options in a write-only manner.
  #
  # This is a view/controller mishmash. As the user clicks here, the model will
  # change. If the model changes, this view will not change.
  class OptionsView extends Backbone.View
    tagName: 'div'

    events:
      'change [name=name]': '_onChangeName'
      'change [name=split_documents]': '_onChangeSplitDocuments'
      'change [name=lang]': '_onChangeLang'

    template: _.template("""
      <form id="import-options-form" class="import-options">
        <fieldset>
          <% if ('name' in options) { %>
            <div class="form-group">
              <label for="import-options-name"><%- t('name.label') %></label>
              <input
                id="import-options-name"
                name="name"
                class="form-control"
                required="required"
                type="text"
                value="<%- options.name %>"
                />
            </div>
          <% } %>

          <% if ('split_documents' in options) { %>
            <div class="form-group">
              <label class="control-label"><%= t('split_documents.label_html') %></label>
              <div class="radio">
                <label>
                  <input type="radio" name="split_documents" <%= options.split_documents ? '' : 'checked="checked"' %> value="false" />
                  <%- t('split_documents.false') %>
                </label>
              </div>
              <div class="radio">
                <label>
                  <input type="radio" name="split_documents" <%= options.split_documents ? 'checked="checked"' : '' %> value="true" />
                  <%- t('split_documents.true') %>
                </label>
              </div>
            </div>
          <% } %>

          <% if ('lang' in options) { %>
            <div class="form-group">
              <label for="import-options-lang"><%- t('lang.label') %></label>
              <select id="import-options-lang" name="lang" class="form-control">
                <% _.each(supportedLanguages, function(language) { %>
                  <option value="<%- language.code %>" <%= options.lang == language.code ? 'selected="selected"' : '' %>>
                    <%- language.name %>
                  </option>
                <% }); %>
              </select>
            </div>
          <% } %>

          <% if ('metadata_json' in options) { %>
            <input type="hidden" name="metadata_json" value="<%- options.metadata_json %>"/>
          <% } %>
        </fieldset>
      </form>
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
      html = @template
        t: t
        supportedLanguages: @model.supportedLanguages
        options: @model.attributes
      @$el.html(html)

      if 'metadata_json' of @model.attributes
        childView = if @model.documentSet
          DocumentMetadataApp.forDocumentSet(@model.documentSet)
        else
          DocumentMetadataApp.forNoDocumentSet()
        childView.setNoDocument()
        childView.model.on 'change', (model) =>
          fields = model.get('fields')
          allJson = model.get('json')
          json = {}
          for k in fields
            json[k] = allJson[k]
          @model.set('metadata_json', JSON.stringify(json))
        @childViews.push(childView)
        childView.render()
        @$el.append(childView.el)

    _onChangeSplitDocuments: (e) ->
      val = @$("[name=split_documents]:checked").val()
      @model.set('split_documents', val == 'true')

    _onChangeLang: (e) ->
      @model.set('lang', Backbone.$(e.target).val())

    _onChangeName: (e) ->
      @model.set('name', Backbone.$(e.target).val())
