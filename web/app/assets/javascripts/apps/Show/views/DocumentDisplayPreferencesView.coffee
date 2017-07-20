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
          <li role="separator" class="divider"></li>
          <li><a target="_blank" class="open-in-new-tab" href=""><i class="icon icon-external-link"></i><%- t('openInNewTab') %></a></li>
        </ul>
      </div>
    ''')

    events:
      'click .switch-text-mode': '_onClickText'
      'click .open-in-new-tab': '_onClickOpenInNewTab'
      'change [name=sidebar]': '_onChangeSidebar'
      'change [name=wrap]': '_onChangeWrap'

    initialize: ->
      throw 'Must pass options.model, a DocumentDisplayPreferences' if !@model

      @listenTo(@model, 'change:text', @render)
      @listenTo(@model, 'change:documentUrl', @render)

      @render()

    render: ->
      @_initialRender() if !@ui

      @ui.text.prop('checked', @model.get('text'))
      @ui.sidebar.prop('checked', @model.get('sidebar'))
      @ui.wrap.prop('checked', @model.get('wrap'))
      @ui.url.attr('href', @model.get('documentUrl'))
      @

    _initialRender: ->
      @$el.html(@template(t: t))

      @ui =
        text: @$('[name=text]')
        sidebar: @$('[name=sidebar]')
        wrap: @$('[name=wrap]')
        url: @$('a.open-in-new-tab')

    _onClickText: ->
      @model.set(text: !@model.get('text'))

    _onChangeSidebar: ->
      @model.set(sidebar: @ui.sidebar.prop('checked'))

    _onChangeWrap: ->
      @model.set(wrap: @ui.wrap.prop('checked'))

    _onClickOpenInNewTab: (e) ->
      if e.currentTarget.getAttribute('href') == ''
        e.preventDefault() # The document has no URL. Ignore the click.
        e.stopPropagation() # Keep the menu open
      # Otherwise, do the default action (open in new tab, close menu)
