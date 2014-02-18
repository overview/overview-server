define [ 'underscore', 'backbone', '../models/User' ], (_, Backbone, User) ->
  class Users extends Backbone.Collection
    model: User

    initialize: (models, options) ->
      @pagination = options.pagination
      throw 'Must set options.pagination, an object with start,size,search?,sortBy?' if !@pagination?

    url: ->
      parts = [ "/admin/users.json?page=#{@pagination.page}" ]
      for key in [ 'search', 'sortBy' ]
        if @pagination[key]
          parts.push("#{key}=#{encodeURIComponent(@pagination[key])}")
      parts.join('&')

    parse: (response) ->
      @trigger('parse-pagination', _.pick(response, 'page', 'pageSize', 'total'))
      super(response.users)
