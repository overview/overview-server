define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentDisplayPreferences')

  # Displays preferences about viewing documents
  class DocumentDisplayPreferencesView extends Backbone.View
    className: 'preferences'

    template: _.template('''
      <div class="switch-text-mode"><input name="text" type="checkbox"
        /><a class="text-off"><%- t('text.false') %></a><a class="text-on"><%- t('text.true') %></a
      ></div
      ><div class="options">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" aria-expanded="false">
          <i class="icon icon-cog"></i>
        </a>
        <ul class="dropdown-menu dropdown-menu-right" role="menu">
          <li><a href="#"><label class="checkbox"><input type="checkbox" name="sidebar"/> <%- t('sidebar') %></label></a></li>
          <li><a href="#"><label class="checkbox"><input type="checkbox" name="wrap"/> <%- t('wrap') %></label></a></li>
        </ul>
      </div>
    ''')

    events:
      'click .switch-text-mode': '_onClickText'
      'change [name=sidebar]': '_onChangeSidebar'
      'change [name=wrap]': '_onChangeWrap'

    initialize: ->
      throw 'Must pass options.model, a DocumentDisplayPreferences' if !@model

      @listenTo(@model, 'change:text', @render)

      @render()

    render: ->
      @_initialRender() if !@ui

      @ui.text.prop('checked', @model.get('text'))
      @ui.sidebar.prop('checked', @model.get('sidebar'))
      @ui.wrap.prop('checked', @model.get('wrap'))
      @

    _initialRender: ->
      @$el.html(@template(t: t))

      @ui =
        text: @$('[name=text]')
        sidebar: @$('[name=sidebar]')
        wrap: @$('[name=wrap]')

    _onClickText: ->
      @model.set(text: !@model.get('text'))

    _onChangeSidebar: ->
      @model.set(sidebar: @ui.sidebar.prop('checked'))

    _onChangeWrap: ->
      @model.set(wrap: @ui.wrap.prop('checked'))
