define [ 'underscore', 'backbone' ], (_, Backbone) ->
  class Controller 
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      @users = options.users
      @paginator = options.paginator
      @mainView = options.mainView

      throw 'must pass options.users, a Backbone.Collection' if !@users?
      throw 'must pass options.paginator, a Paginator' if !@paginator?
      throw 'must pass options.mainView, a Backbone.View' if !@mainView

      @_attachUsers()
      @_attachPaginator()
      @_attachMainView()

    _attachUsers: ->
      @listenTo(@users, 'change:is_admin change:password', @_onUserChanged)
      @listenTo(@users, 'change:deleting', @_onDeletingChanged)
      @listenTo(@users, 'parse-pagination', @_onParsePagination)

    _attachPaginator: ->
      @listenTo(@paginator, 'change', @_onPaginatorChanged)

    _attachMainView: ->
      @listenTo(@mainView, 'create', @create)

    _onUserChanged: (user, __, options) ->
      if !options.resettingPassword
        user.save()
        user.unset('password', resettingPassword: true)

    _onDeletingChanged: (user) ->
      user.destroy
        wait: true
        success: => @users.remove(user)

    _onParsePagination: (pagination) ->
      @paginator.set(pagination, { parsed: true })

    _onPaginatorChanged: (paginator, options) ->
      if !options?.parsed
        @users.pagination = paginator.toJSON()
        @users.fetch(reset: true)

    create: (params) ->
      user = @users.create(params)
