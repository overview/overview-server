define [ 'backbone' ], (Backbone) ->
  Backbone.Model.extend({
    defaults: {
      title: ''
      projectId: 0
      username: null
      password: null
      splitPages: false
    }
  })
