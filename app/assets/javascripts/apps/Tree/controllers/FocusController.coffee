define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  # Alters options.focus based on events
  class FocusController
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.log, a Function' if !options.log
      throw 'Must pass options.animatedTree, an AnimatedTree' if !options.animatedTree
      throw 'Must pass options.focus, an AnimatedFocus' if !options.focus
      throw 'Must pass options.treeView, a TreeView' if !options.treeView
      throw 'Must pass options.focusView, a FocusView' if !options.focusView
      throw 'Must pass options.state, a State' if !options.state

      @log = options.log
      @animatedTree = options.animatedTree
      @focus = options.focus
      @treeView = options.treeView
      @focusView = options.focusView
      @state = options.state

      @_throttledLog = _.throttle(@log, 500)

      @_startListening()

    _startListening: ->
      @listenTo(@state, 'change:documentListParams', @_onChangeDocumentListParams)
      @listenTo(@focusView, 'zoom-pan', @_onFocusViewZoomPan)
      @listenTo(@treeView, 'zoom-pan', @_onTreeViewZoomPan)

    _onChangeDocumentListParams: (state, params) ->
      if (nodeId = params?.node?.id)?
        node = @animatedTree.getAnimatedNode(nodeId)
        parent = node.parent
        @focus.animateNode(parent ? node)

    _onTreeViewZoomPan: (panAndZoom, options) ->
      @_onFocusViewZoomPan(panAndZoom, options)

    _onFocusViewZoomPan: (panAndZoom, options) ->
      zoom = panAndZoom.zoom
      pan = panAndZoom.pan

      @_throttledLog('zoomed/panned', "zoom #{zoom}, pan #{pan}")
      if options?.animate
        @focus.animatePanAndZoom(pan, zoom)
      else
        @focus.setPanAndZoom(pan, zoom)
