define [
  'jquery'
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
], ($, \
    OnDemandTree, PropertyInterpolator, Animator, AnimatedFocus, AnimatedTree, TreeLayout, \
    FocusController, TreeController, \
    FocusView, TreeView) ->

  class TreeApp
    constructor: (options) ->
      throw 'Must pass el, an HTMLElement' if !options.el
      throw 'Must pass app, a Show app' if !options.app
      throw 'Must pass viz, a Viz' if !options.viz
      throw 'Must pass documentSet, a DocumentSet' if !options.documentSet
      throw 'Must pass state, a State' if !options.state

      @$el = $(options.el)
      @$el.html('<div id="tree-app-tree"></div><div id="tree-app-zoom-slider"></div>')

      log = (->)

      els =
        tree: @$el.children('#tree-app-tree')
        zoom: @$el.children('#tree-app-zoom-slider')

      interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
      animator = new Animator(interpolator)
      focus = new AnimatedFocus({}, { animator: animator })

      onDemandTree = new OnDemandTree(options.documentSet, options.state)
      treeLayout = new TreeLayout()
      animatedTree = new AnimatedTree(onDemandTree, options.state, animator, treeLayout, 1, 1)

      @focusView = new FocusView(el: els.zoom, model: focus)
      @treeView = new TreeView(els.tree, options.documentSet, animatedTree, focus)

      @focusController = new FocusController
        log: log
        animatedTree: animatedTree
        focus: focus
        treeView: @treeView
        focusView: @focusView
        state: options.state

      @treeController = new TreeController
        log: log
        state: options.state
        focus: focus
        tree: onDemandTree
        view: @treeView

    remove: ->
      @focusView.remove()
      @treeView.stopListening()#@treeView.remove()
      @focusController.stopListening()
      @treeController.stopListening()
