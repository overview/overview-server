define [ 'backbone' ], (Backbone) ->
  # This class just calls query.fetch() with credentials.
  #
  # Call set({ credentials: Credentials_or_undefined }) to make it
  # call fetch() again. (It calls fetch() during construction, too.)
  #
  # Notice that we never set('query'): we only set its attributes.
  # Watch the status to see what to do with the query: when the
  # fetcher's status is "fetched" then the query's attributes are
  # valid and constant. When the status is anything else, the query's
  # attributes are off-limits.
  #
  # (Rationale for this weird design: it makes views easier to write
  # because the query is a singleton so we'll never render the wrong one.)
  Backbone.Model.extend
    defaults:
      query: undefined
      credentials: undefined
      status: 'error' # start with a prompt

    initialize: ->
      throw 'Must specify query attribute, a DocumentCloudQuery' if !@get('query')

      @on('change:credentials', => @fetchQuery() if @get('credentials')?.isComplete())

      query = @get('query')
      query.on('error', => @set('status', 'error'))
      query.on('sync', => @set('status', 'fetched'))
      query.on('request', => @set('status', 'fetching'))

    # Fetches the query from DocumentCloud.
    #
    # If there is an existing fetch in progress, that one will be aborted and
    # this one will begin.
    #
    # If this object has credentials, those will be used in forging the request.
    # On browsers that don't support CORS, this will send a POST to
    # /documentcloud-proxy/ with the user's email and password. Otherwise, this
    # will GET from https://www.documentcloud.org/api/ with an Authorization header.
    fetchQuery: (options) ->
      @lastFetch?.abort()

      alwaysOptions = {
        timeout: 20000
        complete: => @lastFetch = undefined
      }

      query = @get('query')
      credentials = @get('credentials')

      authOptions = if credentials?
        if $.support.cors
          { headers: credentials.toAuthHeaders() }
        else
          {
            type: 'POST'
            url: query.url().replace(/^.*\/api\//, '/documentcloud-proxy/')
            data: credentials.toPostData()
          }
      else
        {}

      newOptions = _.extend(alwaysOptions, authOptions, options || {})
      @lastFetch = query.fetch(newOptions)
