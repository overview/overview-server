define [
  'underscore'
  'jquery'
  'backbone'
  './models/on_demand_tree'
  './models/property_interpolator'
  './models/animator'
  './models/AnimatedFocus'
  './models/AnimatedTree'
  './models/TreeLayout'
  './controllers/FocusController'
  './controllers/TreeController'
  './views/FocusView'
  './views/TreeView'
], (_, $, Backbone, \
    OnDemandTree, PropertyInterpolator, Animator, AnimatedFocus, AnimatedTree, TreeLayout, \
    FocusController, TreeController, \
    FocusView, TreeView) ->

  class TreeKeyBindings
    constructor: (@treeController) ->

    A: => @treeController.goLeft()
    S: => @treeController.goDown()
    D: => @treeController.goRight()
    W: => @treeController.goUp()
    Left: => @treeController.goLeft()
    Right: => @treeController.goRight()
    Up: => @treeController.goUp()
    Down: => @treeController.goDown()

  class TreeApp
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass el, an HTMLElement' if !options.el
      throw 'Must pass app, a Show app' if !options.app
      throw 'Must pass view, a View' if !options.view
      throw 'Must pass options.keyboardController, a KeyboardController' if !options.keyboardController
      throw 'Must pass state, a State' if !options.state

      @keyboardController = options.keyboardController

      @$el = $(options.el)
      @$el.html('<div id="tree-app-tree"></div><div id="tree-app-zoom-slider"></div>')

      els =
        tree: @$el.children('#tree-app-tree')
        zoom: @$el.children('#tree-app-zoom-slider')

      interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
      animator = new Animator(interpolator)
      focus = new AnimatedFocus({}, { animator: animator })

      @onDemandTree = new OnDemandTree(options.state, options.view)
      treeLayout = new TreeLayout()
      animatedTree = new AnimatedTree(@onDemandTree, options.state, animator, treeLayout, 1, 1)

      @focusView = new FocusView(el: els.zoom, model: focus)
      @treeView = new TreeView(els.tree, options.state, animatedTree, focus)

      @focusController = new FocusController
        animatedTree: animatedTree
        focus: focus
        treeView: @treeView
        focusView: @focusView
        state: options.state

      @treeController = new TreeController
        state: options.state
        focus: focus
        tree: @onDemandTree
        view: @treeView

      @treeKeyBindings = new TreeKeyBindings(@treeController)
      @keyboardController.register(@treeKeyBindings)

      @listenTo @treeView, 'refresh', ->
        # Kill this ViewApp and build a new one
        options.state.setView(null)
        options.state.setView(options.view)

    remove: ->
      @stopListening()
      @keyboardController.unregister(@treeKeyBindings)
      @focusView.remove()
      @treeView.stopListening()#@treeView.remove()
      @focusController.stopListening()
      @treeController.stopListening()
      @$el.remove()
