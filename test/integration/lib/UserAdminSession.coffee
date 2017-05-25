faker = require('faker')
request = require('request')
wdPromise = require('selenium-webdriver/lib/promise')

module.exports = class UserAdminSession
  constructor: (@options) ->
    @request = request.defaults
      baseUrl: @options.baseUrl
      timeout: @options.timeout
      jar: request.jar()

    @promise = wdPromise.fulfilled(null)

    @_login()

  r: (options) ->
    wdPromise.createPromise (resolve, reject) =>
      @request options, (err, response, body) =>
        if err?
          reject(err)
        else
          resolve(response: response, body: body)

  GET: (url) -> @r(method: 'GET', url: url)
  POST: (url, json) -> @r(method: 'POST', url: url, json: json)
  DELETE: (url) -> @r(method: 'DELETE', url: url)

  _then: (code) ->
    @promise = @promise.then(code)

  _login: ->
    @_then(=> @POST('/login', @options.login))

  # Returns a { id, email, is_admin, confirmation_token, ... } Object
  createTemporaryUser: ->
    email = faker.internet.email()
    @createUser(email: email, password: email)

  createUser: (user) ->
    @_then =>
      @POST('/admin/users', user)
        .then((x) -> x.body)

  deleteUser: (user) ->
    @_then(=> @DELETE("/admin/users/#{encodeURIComponent(user.email)}"))

  showUser: (user) ->
    @_then =>
      @GET("/admin/users/#{encodeURIComponent(user.email)}")
        .then((x) -> x.body)
