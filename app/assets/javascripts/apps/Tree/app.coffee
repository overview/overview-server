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
  'i18n'
], (_, $, Backbone, \
    OnDemandTree, PropertyInterpolator, Animator, AnimatedFocus, AnimatedTree, TreeLayout, \
    FocusController, TreeController, \
    FocusView, TreeView, i18n) ->

  t = i18n.namespaced('views.Tree.show')

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
      throw 'Must pass view, a View' if !options.view
      throw 'Must pass options.keyboardController, a KeyboardController' if !options.keyboardController
      throw 'Must pass state, a State' if !options.state

      @keyboardController = options.keyboardController

      @$el = $(options.el)
      @view = options.view
      @keyboardController = options.keyboardController
      @state = options.state

      @render()

    render: ->
      if @view.get('progress') != 1
        @renderProgress()
      else if !@view.get('rootNodeId')
        @renderError()
      else
        @renderTree()

    renderProgress: ->
      @$el.html(_.template("""
        <div class="tree-progress">
          <% if (!view.get('progress')) { %>
            <progress></progress>
          <% } else { %>
            <progress value="<%- view.get('progress') %>"></progress>
          <% } %>
          <p class="description"><%- view.get('progressDescription') %></p>
          <div class="delete">
            <span class="label"><%- t('cancel.label') %></span>
            <button class="btn btn-danger delete"><%- t('cancel') %></button>
          </div>
        </div>
      """)(view: @view, t: t))

      @listenTo @view, 'change:progress', (__, progress) =>
        if progress == 1.0
          @stopListening(@view)
          @render()
        else
          @$el.find('progress').prop('value', progress.toString())
          @$el.find('.description').text(@view.get('progressDescription'))

      @$el.find('button.delete').click (ev) =>
        ev.preventDefault()
        @view.set(deleting: true)
        @view.destroy
          wait: true
          success: => @state.setView(@state.documentSet.views.at(0) || null)

    renderError: ->
      @$el.html(_.template("""
        <div class="tree-error">
          <p class="description"><%- view.get('progressDescription') %></p>
        </div>
      """)(view: @view))

    renderTree: ->
      @$el.html('<div id="tree-app-tree"></div><div id="tree-app-zoom-slider"></div>')

      els =
        tree: @$el.children('#tree-app-tree')
        zoom: @$el.children('#tree-app-zoom-slider')

      interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
      animator = new Animator(interpolator)
      focus = new AnimatedFocus({}, { animator: animator })

      @onDemandTree = new OnDemandTree(@state, @view)
      treeLayout = new TreeLayout()
      animatedTree = new AnimatedTree(@onDemandTree, @state, animator, treeLayout, 1, 1)

      @focusView = new FocusView(el: els.zoom, model: focus)
      @treeView = new TreeView(els.tree, @state, animatedTree, focus)

      @focusController = new FocusController
        animatedTree: animatedTree
        focus: focus
        treeView: @treeView
        focusView: @focusView
        state: @state

      @treeController = new TreeController
        state: @state
        focus: focus
        tree: @onDemandTree
        view: @treeView

      @treeKeyBindings = new TreeKeyBindings(@treeController)
      @keyboardController.register(@treeKeyBindings)

      @listenTo @treeView, 'refresh', =>
        # Kill this ViewApp and build a new one
        @state.setView(null)
        @state.setView(@view)

    remove: ->
      @stopListening()
      @keyboardController?.unregister(@treeKeyBindings)
      @focusView?.remove()
      @treeView?.stopListening()#@treeView.remove()
      @focusController?.stopListening()
      @treeController?.stopListening()
      @$el.remove()
