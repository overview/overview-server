Promise = require('bluebird')

# A normal-user API
module.exports = class NormalUser
  constructor: (@client, @username) ->
    @login(@username)

  login: (username) ->
    @client.GET("/login") # set csrfToken cookie
    @client.POST("/login", email: username, password: username)

  cloneExampleDocumentSet: (documentSetId) ->
    @client.POST("/imports/clone/#{documentSetId}", {})

  pollImportJobsUntilDone: ->
    Promise.delay(500)
      .then(=> @client.GET("/imports.json"))
      .then (response) =>
        if response.body == '[]'
          null
        else
          @pollImportJobsUntilDone()
