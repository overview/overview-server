define [ 'underscore', 'backbone' ], (_, Backbone) ->
  # A DocumentCloud query.
  #
  # Fields:
  # * title: The title
  # * description: The user-supplied description, if it's a projectid:1-slug query
  # * document_count: a count of all documents
  Backbone.Model.extend
    defaults: {
      title: ''
      description: ''
      document_count: 0
    }

    _projectSlug: ->
      if m = /^\s*projectid:\s*([-a-z0-9]+)\s*$/.exec(@id)
        m[1]
      else
        undefined

    _projectId: ->
      slug = @_projectSlug()
      if slug?
        parseInt(slug, 10)
      else
        undefined

    url: ->
      slug = @_projectSlug()
      if slug?
        "https://www.documentcloud.org/api/projects/#{slug}.json?include_document_ids=false"
      else
        "https://www.documentcloud.org/api/search?q=#{encodeURIComponent(@id)}&per_page=0"

    parse: (response, options) ->
      # BUG in DocumentCloud returns all projects instead of the one we want.
      # See https://github.com/documentcloud/documentcloud/issues/57
      #
      # When that bug is resolved, remove this branch
      if response.projects?
        projectId = @_projectId()
        project = _.find(response.projects || [], (p) -> parseInt("#{p.id}", 10) == projectId)
        # BUG in DocumentCloud returns all documents instead of none. Remove
        # these two lines when it's fixed.
        #
        # See https://github.com/documentcloud/documentcloud/issues/25
        project.document_count ||= project.document_ids?.length || 0
        project
      else if response.project?
        project = response.project
        # BUG in DocumentCloud returns all documents instead of none. Remove
        # these two lines when it's fixed.
        #
        # See https://github.com/documentcloud/documentcloud/issues/25
        project.document_count ||= project.document_ids?.length || 0
        project
      else
        # search results
        {
          title: response.q
          description: ''
          document_count: response.total
        }
