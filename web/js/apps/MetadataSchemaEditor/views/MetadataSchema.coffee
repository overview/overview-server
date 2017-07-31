define [
  'underscore'
  'backbone'
  'html5sortable'
  'i18n'
], (_, Backbone, html5sortable, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.MetadataSchemaEditor.views.MetadataSchema')

  NewField =
    name: ''
    type: 'String'
    display: 'TextInput'

  readFieldFromTr = (tr) ->
    name: tr.querySelector('input[name=name]').value.trim()
    type: 'String'
    display: tr.querySelector('select[name=display]').value

  class MetadataSchema extends Backbone.View
    className: 'metadata-schema'

    templates:
      main: _.template '''
        <form method="get" action="#">
          <table>
            <thead>
              <tr>
                <th></th>
                <th class="name"><%- t('th.name') %></th>
                <th class="display"><%- t('th.display') %></th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <% fields.forEach(function(field) { %>
                <%= fieldTemplate({ t: t, field: field }) %>
              <% }) %>
            </tbody>
            <tfoot>
              <tr>
                <td></td>
                <td colspan="3"><button class="btn add-field"><%- t('addField') %></button></td>
              </tr>
            </tfoot>
          </table>
        </form>
      '''

      field: _.template '''
        <tr>
          <td class="sort-handle"><i class="icon icon-arrows-v"></i></td>
          <td class="name"><input type="text" name="name" class="input-sm form-control" placeholder="<%- t('name.placeholder') %>" value="<%- field.name %>"></td>
          <td class="display"><select name="display" class="input-sm form-control">
            <option value="TextInput" <%= field.display === 'TextInput' ? 'selected' : '' %>><%- t('display.TextInput') %></option>
            <option value="Div" <%= field.display === 'Div' ? 'selected' : '' %>><%- t('display.Div') %></option>
            <option value="Pre" <%= field.display === 'Pre' ? 'selected' : '' %>><%- t('display.Pre') %></option>
          </select></td>
          <td class="actions"><a href="#" class="remove"><%- t('remove') %></a></td>
        </tr>
      '''

    events:
      change: 'onChangeForm'
      'submit form': 'onSubmitForm'
      'click button.add-field': 'onClickAddField'
      'click a.remove': 'onClickRemoveField'

    initialize: (options) ->
      if !options.documentSet
        throw 'Must specify options.documentSet, a Backbone.Model with a `metadataSchema` attribute'

      @documentSet = options.documentSet

      @listenTo(@documentSet, 'change:metadataSchema', @onChangeMetadataSchema)
      @render()

    onChangeMetadataSchema: (_1, metadataSchema, options) ->
      return if options.cause == 'userEntry'
      @render()

    onSubmitForm: (ev) ->
      ev.preventDefault()
      @submit()

    onChangeForm: (ev) ->
      @submit()

    submit: ->
      schema = @readMetadataSchemaFromHtml()
      @documentSet.patchMetadataSchema(schema, cause: 'userEntry')

    readMetadataSchemaFromHtml: ->
      trs = Array.prototype.slice.apply(@el.querySelectorAll('tbody tr'))

      version: 1
      fields: trs.map(readFieldFromTr).filter((field) => field.name)

    render: ->
      @el.innerHTML = @templates.main
        t: t
        fields: @documentSet.get('metadataSchema').fields
        fieldTemplate: @templates.field
      html5sortable(@el.querySelector('tbody'), handle: '.sort-handle')[0]
        .addEventListener('sortupdate', () => @onSortupdate())
      @

    onSortupdate: ->
      @submit()

    onClickAddField: (ev) ->
      html = @templates.field(t: t, field: NewField)
      @$('tbody').append(Backbone.$(html))
      @el.querySelector('tbody tr:last-child input[name=name]').focus()

    onClickRemoveField: (ev) ->
      ev.preventDefault()
      tr = ev.target.parentNode.parentNode
      fieldName = tr.querySelector('input[name=name]').value

      if window.confirm(t('confirmRemove', fieldName))
        tr.parentNode.removeChild(tr)
        @submit()
