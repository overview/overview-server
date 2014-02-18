define [
  'underscore'
  'backbone'
  './UsersView'
  './PaginatorView'
  './UserView'
  './NewUserView'
  'i18n'
], (_, Backbone, UsersView, PaginatorView, UserView, NewUserView, i18n) ->
  t = i18n.namespaced("views.admin.User.index")

  class MainView extends Backbone.View
    template: _.template("""
      <table class="users table table-hover">
        <thead>
          <tr>
            <th class="email"><%- t("th.email") %></th>
            <th class="admin"><%- t("th.admin") %></th>
            <th class="confirmed-at"><%- t("th.confirmed_at") %></th>
            <th class="last-activity-at"><%- t("th.last_activity_at") %></th>
            <th class="actions"><%- t("th.actions") %></th>
          </tr>
        </thead>
      </table>
      """)

    initialize: (options) ->
      @paginator = options.paginator
      @users = options.users
      @adminEmail = options.adminEmail

      throw 'Must pass options.users, a Backbone.Collection' if !@users?
      throw 'Must pass options.paginator, a Backbone.Model' if !@paginator?
      throw 'Must pass options.adminEmail, a String' if !@adminEmail?

      @paginatorView = new PaginatorView(model: @paginator)
      @usersView = new UsersView(collection: @users, adminEmail: @adminEmail, modelView: UserView)
      @newUserView = new NewUserView()

      @listenTo(@newUserView, 'create', @_onCreate)

    remove: ->
      @paginatorView.remove()
      @usersView.remove()
      @newUserView.remove()
      super()

    render: ->
      @usersView.render()
      @paginatorView.render()
      @newUserView.render()

      html = @template(t: t)
      @$el.html(html)
      @$el.prepend(@paginatorView.el)
      @$('table').append(@usersView.el)
      @$('table').append(@newUserView.el)

    _onCreate: (params) ->
      @trigger('create', params)
