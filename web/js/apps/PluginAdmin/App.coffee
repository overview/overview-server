define [
  'underscore'
  './models/Plugin'
  './collections/Plugins'
  'backbone'
  'backform'
  'backgrid'
  'i18n'
], (_, Plugin, Plugins, Backbone, Backform, Backgrid, i18n) ->
  t = i18n.namespaced('views.admin.Plugin.index')

  class DeleteCell extends Backgrid.Cell
    className: 'delete-cell'
    template: _.template('''<button class="btn btn-link delete"><%- t('action.delete') %></button>''')
    render: -> @$el.html(@template(t: t)); @
    events: { 'click .delete': 'onDelete' }
    onDelete: (e) ->
      e.preventDefault()
      @model.destroy()

  class AutocreateCell extends Backgrid.Cell
    className: 'autocreate-cell'

    template: _.template('''
      <input type="checkbox" name="autocreate" />
      <label class="autocreate-order">
        <%- t('label.order') %>
        <input type="number" name="autocreateOrder" />
      </label>
    ''')

    render: ->
      @$el.html(@template(t: t))
      @ui =
        checkbox: @$('input[name=autocreate]')
        label: @$('label')
        order: @$('input[name=autocreateOrder]')

      @ui.checkbox.prop('checked', !!@model.get('autocreate'))
      @ui.order.val(@model.get('autocreateOrder') || '')
      @_updateOrderEnabled()

      @

    events:
      'change input[name=autocreate]': '_onChangeAutocreate'
      'change input[name=autocreateOrder]': '_onChangeAutocreateOrder'

    _onChangeAutocreate: ->
      @_updateOrderEnabled()
      @_updateModel()
      if @$el.hasClass('order-enabled')
        @ui.order.focus().select()

    _onChangeAutocreateOrder: ->
      @_updateModel()

    _updateOrderEnabled: ->
      checked = @ui.checkbox.prop('checked')
      @ui.label.toggleClass('disabled', !checked)
      @ui.order.prop('disabled', !checked)
      @$el.toggleClass('order-enabled', checked)

    _updateModel: ->
      checked = @ui.checkbox.prop('checked')
      @model.set
        autocreate: checked
        autocreateOrder: checked && parseInt(@ui.order.val(), 10) || 0

  class App
    constructor: (options) ->
      throw 'Must pass options.el, an HTMLElement' if !options.el

      plugins = new Plugins([])
      plugins.fetch()
      plugins.on('change', (plugin) -> plugin.save())

      grid = new Backgrid.Grid
        className: 'table'
        collection: plugins
        columns: [
          {
            name: 'name'
            label: t('th.name')
            cell: 'string'
            sortable: false
          }
          {
            name: 'description'
            label: t('th.description')
            cell: 'string'
            sortable: false
          }
          {
            name: 'url'
            label: t('th.url')
            cell: 'string'
            sortable: false
          }
          {
            name: 'autocreate'
            label: t('th.autocreate')
            cell: AutocreateCell
            sortable: false
          }
          {
            name: 'delete'
            label: ''
            cell: DeleteCell
            sortable: false
          }
        ]

      grid.render()

      Backform.formClassName = ''
      Backform.controlLabelClassName = 'control-label'
      Backform.controlsClassName = ''
      form = new Backform.Form
        model: new Plugin()
        fields: [
          { name: 'name', label: t('new.name'), control: 'input', placeholder: 'Tree', required: true }
          { name: 'description', label: t('new.description'), control: 'input', placeholder: 'Organizes documents into folders', required: true }
          { name: 'url', label: t('new.url'), control: 'input', type: 'url', placeholder: 'https://overview-tree.s3.amazon.com', required: true }
          { control: 'button', value: t('new.submit') }
        ]
      form.render()
      form.$el.on 'submit', (e) ->
        e.preventDefault()
        json = form.model.toJSON()
        plugins.create(json)
        form.el.reset()

      $el = Backbone.$(options.el)

      $el.append(grid.el)
      $el.append(Backbone.$('<h2></h2>').text(t('new.heading')))
      $el.append(form.el)
