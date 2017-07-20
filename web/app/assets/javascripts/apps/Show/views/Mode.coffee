define [ 'backbone' ], (Backbone) ->
  # Sets classes in our HTML based on what we're viewing.
  #
  # Usage:
  #
  #   new Mode({
  #     state: state # a State
  #     el: $('#main')[0]
  #   })
  #
  # Classes:
  #
  # * div#main.document-selected: one document selected (and perhaps nodes/tags)
  Backbone.View.extend
    initialize: ->
      throw 'Must set options.state, a State' if !@options.state
      throw 'Must set options.el, an HTMLElement' if !@options.el

      @state = @options.state

      @listenTo(@state, 'change:document', @render)
      @render()

    render: ->
      @$el.toggleClass('document-selected', @state.get('document')?)
