define [ 'backbone' ], (Backbone) ->
  class UsersView extends Backbone.View
    tagName: 'tbody'

    initialize: (options) ->
      @adminEmail = options.adminEmail
      @modelView = options.modelView
      throw 'must pass collection, a Backbone.Collection' if !@collection
      throw 'must pass adminEmail, the email of the current user' if !@adminEmail
      throw 'must pass modelView, a Backbone.View class' if !@modelView

      @_cidToView = {}

      @listenTo(@collection, 'add', @_onAdd)
      @listenTo(@collection, 'reset', @_onReset)
      @listenTo(@collection, 'remove', @_onRemove)

    _onAdd: (model) ->
      childView = new @modelView(model: model, adminEmail: @adminEmail)
      @_cidToView[model.cid] = childView
      childView.render()
      @$el.append(childView.el)

    _onRemove: (model) ->
      childView = @_cidToView[model.cid]
      childView.remove()
      delete @_cidToView[model.cid]
      undefined

    _onReset: ->
      @_removeChildViews()
      @render()

    _removeChildViews: ->
      @$el.empty()
      for __, v of @_cidToView
        v.remove()
      @_cidToView = {}
      undefined

    render: ->
      for model in @collection.models
        cid = model.cid
        if cid not of @_cidToView
          childView = new @modelView(model: model, adminEmail: @adminEmail)
          @_cidToView[cid] = childView
          @_cidToView[cid].render()
          @$el.append(childView.el)
      this

    remove: ->
      @_removeChildViews()
      super()
