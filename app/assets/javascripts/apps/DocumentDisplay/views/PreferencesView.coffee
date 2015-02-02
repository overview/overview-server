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
      <div class="compact">
        <a href="#" class="expand">
          <span class="showing"></span>
        </a>
      </div>
      <div class="expanded">
        <a href="#" class="collapse"><%- t('collapse.top') %></a>
        <form>
          <fieldset class="showing">
            <legend><%- t('showing.expanded') %></legend>
            <div class="radio-inline">
              <label>
                <input type="radio" name="text" value="false" checked> <%- t('text.false') %>
              </label>
            </div>
            <div class="radio-inline">
              <label>
                <input type="radio" name="text" value="true"> <%- t('text.true') %>
              </label>
            </div>
            <p class="help-block text-disabled"><%- t('text.disabled') %></p>
          </fieldset>

          <fieldset class="options">
            <legend class="options"><%- t('options') %></legend>
            <div class="checkbox sidebar">
              <label>
                <input type="checkbox" name="sidebar"> <%- t('sidebar') %>
              </label>
            </div>
            <div class="checkbox wrap">
              <label>
                <input type="checkbox" name="wrap"> <%- t('wrap') %>
              </label>
            </div>
          </fieldset>
        </form>
        <a href="#" class="collapse"><%- t('collapse.bottom') %></a>
      </div>
    ''')

    events:
      'click a.expand': '_onClickExpand'
      'click a.collapse': '_onClickCollapse'
      'change [name=text]': '_onChangeText'
      'change [name=sidebar]': '_onChangeSidebar'
      'change [name=wrap]': '_onChangeWrap'

    initialize: (options) ->
      throw 'Must pass options.preferences, a Preferences' if !options.preferences
      throw 'Must pass options.currentCapabilities, a CurrentCapabilities' if !options.currentCapabilities

      @preferences = options.preferences
      @currentCapabilities = options.currentCapabilities

      @listenTo(@currentCapabilities, 'change', @render)
      @listenTo(@preferences, 'change:text', @render)

      @render()

    render: ->
      @_initialRender() if !@ui

      @$el.toggleClass('can-show-document', @currentCapabilities.get('canShowDocument') == true)
      @$el.toggleClass('can-show-sidebar', @currentCapabilities.get('canShowSidebar') == true)
      @$el.toggleClass('can-wrap', @currentCapabilities.get('canWrap') == true)
      @ui.showingFieldset.attr('disabled', @currentCapabilities.get('canShowDocument') != true)
      @ui.showing.text(@currentCapabilities.get('canShowDocument') == true && !@preferences.get('text') && t('showing.document') || t('showing.text'))
      @

    _initialRender: ->
      @$el.html(@template(t: t))

      @ui =
        showing: @$('span.showing')
        showingFieldset: @$('fieldset.showing')
        text: @$('[name=text]')
        sidebar: @$('[name=sidebar]')
        wrap: @$('[name=wrap]')

      @ui.text.filter("[value=#{@preferences.get('text')}]").prop('checked', true)
      @ui.sidebar.prop('checked', @preferences.get('sidebar'))
      @ui.wrap.prop('checked', @preferences.get('wrap'))

    _onClickExpand: (e) ->
      e.preventDefault()
      @$el.toggleClass('expanded')

    _onClickCollapse: (e) ->
      e.preventDefault()
      @$el.removeClass('expanded')

    hide: (e) ->
      @$el.removeClass('expanded')

    _onChangeText: ->
      @preferences.set(text: @ui.text.filter(':checked').val() == 'true')

    _onChangeSidebar: ->
      @preferences.set(sidebar: @ui.sidebar.prop('checked'))

    _onChangeWrap: ->
      @preferences.set(wrap: @ui.wrap.prop('checked'))
