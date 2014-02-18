define [
  './controllers/Controller'
  './collections/Users'
  './models/Paginator'
  './views/MainView'
], (Controller, Users, Paginator, MainView) ->
  # Controls user administration
  class App
    constructor: (options) ->
      paginator = new Paginator
      users = new Users([], pagination: paginator.toJSON())

      @el = options.el

      @view = new MainView
        el: @el
        adminEmail: options.adminEmail
        users: users
        paginator: paginator
      @view.render()

      @controller = new Controller
        users: users
        paginator: paginator
        mainView: @view

      users.fetch(reset: true)

    # Disconnects all listeners
    remove: ->
      @controller.stopListening()
