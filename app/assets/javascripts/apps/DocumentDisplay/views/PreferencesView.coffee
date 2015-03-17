define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.Document.show.PreferencesView')

  # Displays preferences about viewing documents
  class PreferencesView extends Backbone.View
    className: 'preferences'

    template: _.template('''
      <div class="switch-text-mode"><input name="text" type="checkbox"
        /><a class="text-off"><%- t('text.false') %></a><a class="text-on"><%- t('text.true') %></a
      ></div
      ><div class="options">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" aria-expanded="false">
          <i class="icon-cog"></i>
        </a>
        <ul class="dropdown-menu dropdown-menu-right" role="menu">
          <li><a href="#"><label class="checkbox"><input type="checkbox" name="sidebar"/> <%- t('sidebar') %></label></a></li>
          <li><a href="#"><label class="checkbox"><input type="checkbox" name="wrap"/> <%- t('wrap') %></label></a></li>
        </ul>
      </div>
    ''')

    events:
      'click a.expand': '_onClickExpand'
      'click a.collapse': '_onClickCollapse'
      'click .switch-text-mode': '_onClickText'
      'change [name=sidebar]': '_onChangeSidebar'
      'change [name=wrap]': '_onChangeWrap'

    initialize: (options) ->
      throw 'Must pass options.preferences, a Preferences' if !options.preferences

      @preferences = options.preferences
      @listenTo(@preferences, 'change:text', @render)

      @render()

    render: ->
      @_initialRender() if !@ui

      @ui.text.prop('checked', @preferences.get('text'))
      @ui.sidebar.prop('checked', @preferences.get('sidebar'))
      @ui.wrap.prop('checked', @preferences.get('wrap'))
      @

    _initialRender: ->
      @$el.html(@template(t: t))

      @ui =
        text: @$('[name=text]')
        sidebar: @$('[name=sidebar]')
        wrap: @$('[name=wrap]')

    _onClickExpand: (e) ->
      e.preventDefault()
      @$el.toggleClass('expanded')

    _onClickCollapse: (e) ->
      e.preventDefault()
      @$el.removeClass('expanded')

    hide: (e) ->
      @$el.removeClass('expanded')

    _onClickText: ->
      @preferences.set(text: !@preferences.get('text'))

    _onChangeSidebar: ->
      @preferences.set(sidebar: @ui.sidebar.prop('checked'))

    _onChangeWrap: ->
      @preferences.set(wrap: @ui.wrap.prop('checked'))
