define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.admin.User.index')

  class UserView extends Backbone.View
    tagName: 'tr'

    template: _.template("""
      <td class="email"><%- model.get('email') %></td>
      <td class="is-admin">
        <% if (model.get('is_admin')) { %>
          <%- t('td.is_admin.true') %> <%= model.get('email') != adminEmail ? ('<a href="#" class="demote">' + t('action.demote') + '</a>') : '' %>
        <% } else { %>
          <%- t('td.is_admin.false') %> <a href="#" class="promote"><%- t('action.promote') %></a>
        <% } %>
      <td class="confirmed-at" title="<%- model.has('confirmed_at') ? model.getDate('confirmed_at').toString() : '' %>">
        <%- t('td.confirmed_at', model.getDate('confirmed_at')) %>
      </td>
      <td class="last-activity-at" title="<%- model.has('last_activity_at') ? model.getDate('last_activity_at').toString() : '' %>">
        <%- t('td.last_activity_at', model.getDate('last_activity_at')) %>
      </td>
      <td class="actions">
        <% if (model.get('email') != adminEmail) { %>
          <a href="#" class="delete"><%- t('action.delete') %></a>
        <% } %>
        <a href="#" class="change-password"><%- t('action.changePassword') %></a>
        <form class="change-password form-inline" style="display: none;">
          <label>
            <%- t('changePassword.label') %>
            <input name="password" type="password" required="required"/>
          </label>
          <input type="submit" class="btn btn-primary" value="<%- t('changePassword.submit') %>"/>
          <input type="reset" class="btn" value="<%- t('changePassword.reset') %>"/>
        </form>
      </td>
      """)

    events:
      'click .demote': '_onDemote'
      'click .promote': '_onPromote'
      'click .delete': '_onDelete'
      'click a.change-password': '_onStartChangePassword'
      'submit form.change-password': '_onSubmitChangePassword'
      'reset form.change-password': '_onResetChangePassword'

    initialize: (options) ->
      @adminEmail = options.adminEmail
      throw 'Must set model, a Backbone.Model' if !@model
      throw 'Must set adminEmail, the email of the current administrator' if !@adminEmail

      @listenTo(@model, 'change', @render)
      @listenTo(@model, 'request', @_onRequest)
      @listenTo(@model, 'sync', @_onSync)
      @listenTo(@model, 'error', @_onError)

    render: ->
      html = @template(model: @model, adminEmail: @adminEmail, t: t)
      @$el.html(html)

    _onRequest: -> @$el.addClass('updating')
    _onSync: -> @$el.removeClass('updating')
    _onError: -> @$el.addClass('error')

    _onDemote: (e) ->
      e.preventDefault()
      @model.set(is_admin: false)

    _onPromote: (e) ->
      e.preventDefault()
      @model.set(is_admin: true)

    _onDelete: (e) ->
      e.preventDefault()
      if window.confirm(t('confirm.delete', @model.get('email')))
        @model.set(deleting: true)

    _onStartChangePassword: (e) ->
      e.preventDefault()
      @$('a.change-password').hide()
      @$('form.change-password').show()
      @$('form.change-password input[name=password]').focus()

    _onSubmitChangePassword: (e) ->
      e.preventDefault()
      $form = @$('form.change-password')
      newPassword = $form.find('input[name=password]').val()
      $form.get(0).reset()
      @model.set(password: newPassword)

    _onResetChangePassword: (e) ->
      # do not preventDefault()
      @$('form.change-password').hide()
      @$('a.change-password').show()
