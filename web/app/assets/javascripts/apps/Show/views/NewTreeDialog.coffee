define [
  'underscore'
  'jquery'
  'i18n'
  'bootstrap-modal'
  'jquery.validate'
], (_, $, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.NewTreeDialog')

  # Builds a new Tree.
  #
  # Ought to be unit-tested. Isn't.
  class NewTreeDialog
    template: _.template('''
      <form class="tree-options modal" method="post" action="#">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header">
              <a href="#" class="close" data-dismiss="modal" aria-hidden="true">×</a>
              <h4 class="modal-title"><%- t('title') %></h4>
            </div>
            <div class="modal-body">
              <fieldset>
                <div class="form-group">
                  <label for="tree-options-tree-title"><%- t('tree_title.label') %></label>
                  <input id="tree-options-tree-title" name="tree_title" class="form-control" required="required" type="text" value="">
                </div>
                <div class="form-group">
                  <label for="tree-options-tag-id"><%- t('tag_id.label') %></label>
                  <div class="">
                    <select id="tree-options-tag-id" name="tag_id" class="form-control">
                      <option selected="selected" value=""><%- t('tag.allDocuments') %></option>
                      <% tags.forEach(function(tag) { %>
                        <option value="<%- tag.id %>"><%- t('tag.name', tag.get('name')) %></option>
                      <% }); %>
                    </select>
                  </div>
                </div>
                <div class="form-group">
                  <label for="tree-options-lang"><%- t('lang.label') %></label>
                  <select id="tree-options-lang" name="lang" class="form-control">
                    <% supportedLanguages.forEach(function(lang) { %>
                      <option value="<%- lang.code %>" <%= defaultLanguageCode == lang.code ? 'selected' : '' %>>
                        <%- lang.name %>
                      </option>
                    <% }); %>
                  </select>
                </div>
                <div class="form-group">
                  <label for="tree-options-supplied-stop-words"><%= t('supplied_stop_words.label_html') %></label>
                  <textarea id="tree-options-supplied-stop-words" name="supplied_stop_words" class="form-control" rows="3"></textarea>
                  <p class="help-block"><%= t('supplied_stop_words.help') %></p>
                </div>
                <div class="form-group">
                  <label for="tree-options-important-words"><%= t('important_words.label_html') %></label>
                  <textarea id="tree-options-important-words" name="important_words" class="form-control" rows="3"></textarea>
                  <p class="help-block"><%- t('important_words.help') %></p>
                </div>
              </fieldset>
            </div>
            <div class="modal-footer">
              <button class="btn btn-default" type="reset"><%- t('reset') %></button>
              <button class="btn btn-primary" type="submit"><%- t('submit') %></button>
            </div>
          </div>
        </div>
      </form>
    ''')

    constructor: (options) ->
      throw 'Must pass options.submit, a function that accepts a POST query string' if !options.submit
      throw 'Must pass options.tags, a Backbone.Collection of Tag Models' if !options.tags
      throw 'Must pass options.supportedLanguages, an Array of {code,name} Objects' if !options.supportedLanguages
      throw 'Must pass options.defaultLanguageCode, a String language code' if !options.defaultLanguageCode

      @$container = $(options.container ? 'body')

      html = @template
        t: t
        tags: options.tags
        supportedLanguages: options.supportedLanguages
        defaultLanguageCode: options.defaultLanguageCode

      @$el = $(html)

      @$el
        .appendTo(@$container)
        .on('reset', => @$el.modal('hide'))
        .on('shown.bs.modal', => @$el.find('input:eq(0)').focus().select())
        .on('hidden.bs.modal', => @remove())
        .modal('show')

      @$el.validate
        submitHandler: =>
          @$el.find('[type=submit]').html('<i class="icon icon-spinner icon-spin"></i>')

          data = @$el.serialize()
          options.submit(data)
          false # don't fall through to submit

      # Try and jump the gun, if the browser will let us
      @$el.find('input:eq(0)').focus().select()

    remove: ->
      @$el.remove()
      @$el.off()
      $(document).off('.bs.modal')
