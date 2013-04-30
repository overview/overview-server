define [ 'backbone' ], (Backbone) ->
  # This class just calls project.fetch() with credentials.
  #
  # Call set({ credentials: Credentials_or_undefined }) to make it
  # call fetch() again. (It calls fetch() during construction, too.)
  #
  # Notice that the project is the same object: its attributes change.
  # Watch the status to see what to do with the project: when the
  # fetcher's status is "fetched" then the project's attributes are
  # value and constant. When the status is anything else, the project's
  # attributes are off-limits.
  Backbone.Model.extend
    defaults:
      project: undefined
      credentials: undefined
      status: 'unknown'

    initialize: ->
      @on('change:credentials', => @fetchProject())

      project = @get('project')
      project.on('error', => @set('status', 'error'))
      project.on('sync', => @set('status', 'fetched'))
      project.on('request', => @set('status', 'fetching'))

      @fetchProject()

    fetchProject: ->
      @get('project').fetch(@get('credentials'))
