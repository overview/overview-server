Client = require('./Client')
NormalUser = require('./NormalUser')

# A user-admin API
module.exports = class Admin
  constructor: (@baseUrl, username, password) ->
    @client = new Client(baseUrl: @baseUrl, title: username)
    @login(username, password)

  login: (username, password) ->
    @client.GET("/login") # set csrfToken cookie
    @client.POST("/login", email: username, password: password)

  addUser: (username) ->
    attributes = { email: username, password: username }
    promise = @client.PUT("/admin/users/#{encodeURIComponent(username)}", attributes, checkStatus: false)
      .then (response) =>
        if response.statusCode == 404
          @client.POST("/admin/users", email: username, password: username)
        else
          throw new Error("Wrong status code from server: #{response.statusCode}") if !(200 <= response.statusCode < 400)
    client = new Client(baseUrl: @baseUrl, title: username, startAfterPromise: promise)
    new NormalUser(client, username)
