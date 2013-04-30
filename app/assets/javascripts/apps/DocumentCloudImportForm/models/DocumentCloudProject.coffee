define [ 'underscore', 'backbone' ], (_, Backbone) ->
  # A DocumentCloud project.
  #
  # Fields:
  # * title: The title
  # * description: The user-supplied description
  # * documentIds: a list of all document IDs.
  #   (see https://github.com/documentcloud/documentcloud/issues/25)
  # * credentials: Credentials model used during fetch. May be undefined.
  Backbone.Model.extend
    defaults: {
      title: ''
      description: ''
      document_ids: []
    }

    url: -> "https://www.documentcloud.org/api/projects/#{@id}.json"

    # BUG in DocumentCloud returns all projects instead of the one we want.
    # See https://github.com/documentcloud/documentcloud/issues/57
    #
    # When that bug is resolved, remove this method (or make it return
    # response.project, depending on how DocumentCloud does things).
    parse: (response, options) ->
      _.find(response.projects || [], (p) => "#{p.id}" == "#{@id}")

    # Fetches the project from DocumentCloud.
    #
    # If there is an existing fetch in progress, that one will be aborted and
    # this one will begin.
    #
    # The options parameter may include a Credentials object. This will be used
    # in forging the request. On browsers that don't support CORS, this will
    # send a POST to /documentcloud-proxy/ with the user's email and password.
    # Otherwise, it will GET from www.documentcloud.org/api/ with an
    # Authorization header.
    fetch: (credentials, options) ->
      @lastFetch?.abort()

      alwaysOptions = {
        timeout: 60000
        complete: => @lastFetch = undefined
      }

      authOptions = if credentials?
        if $.support.cors
          { headers: credentials.toAuthHeaders() }
        else
          {
            type: 'POST'
            url: @url().replace(/^.*\/api\//, '/documentcloud-proxy/')
            data: credentials.toPostData()
          }
      else
        {}

      newOptions = _.extend(alwaysOptions, authOptions, options || {})
      @lastFetch = Backbone.Model.prototype.fetch.call(this, newOptions)
