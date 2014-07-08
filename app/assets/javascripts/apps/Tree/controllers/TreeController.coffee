define [
  'underscore'
  'backbone'
], (_, Backbone) ->
  class TreeController
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.focus, a Focus' if !options.focus
      throw 'Must pass options.tree, an OnDemandTree' if !options.tree
      throw 'Must pass options.view, a TreeView' if !options.view

      @state = options.state
      @focus = options.focus
      @tree = options.tree
      @view = options.view
      @requestAnimationFrame = (options.requestAnimationFrame || window.requestAnimationFrame).bind(window)

      @_attach()
      @_init()

    _attach: ->
      @listenTo(@view, 'needs-update', @_animate)
      @listenTo(@view, 'click', @_onClick)
      @listenTo(@view, 'expand', @_onExpand)
      @listenTo(@view, 'collapse', @_onCollapse)

    _init: ->
      @_animating = false
      @_animate()

    _animate: ->
      animate_frame = =>
        if @view.needsUpdate()
          @view.update()
          @requestAnimationFrame(animate_frame)
        else
          @_animating = false

      if !@_animating
        @_animating = true
        @requestAnimationFrame(animate_frame)

    _onClick: (maybeNode) ->
      return if !maybeNode?
      node = maybeNode
      @tree.demandNode(node.id)

      oldParams = @state.get('documentListParams')
      newParams = @state.get('documentListParams').reset.byNode(node)
      if newParams.equals(oldParams)
        @state.set
          document: null
          oneDocumentSelected: false
      else
        @state.set
          document: null
          documentListParams: newParams

    _onExpand: (node) ->
      @tree.demandNode(node.id)

    _onCollapse: (node) ->
      @tree.unloadNodeChildren(node.id)

      params = @state.get('documentListParams')
      selectedNodeId = params?.node?.id
      if selectedNodeId && @tree.id_tree.is_id_ancestor_of_id(node.id, selectedNodeId)
        @state.set
          document: null
          documentListParams: params.reset.byNode(node)

    _selectNode: (node) ->
      @state.resetDocumentListParams().byNode(node)
      @tree.demandNode(node.id)

    _findNodeRelativeToSelectedNode: (finder) ->
      nodeId = @state.get('documentListParams')?.node?.id || null
      return @tree.getRoot() if !nodeId?
      newId = @view[finder](nodeId)
      if newId?
        @tree.getNode(newId)
      else
        null

    _go: (finder) ->
      newNode = @_findNodeRelativeToSelectedNode(finder)
      @_selectNode(newNode) if newNode?

    goUp: -> @_go('nodeid_above')
    goDown: -> @_go('nodeid_below')
    goLeft: -> @_go('nodeid_left')
    goRight: -> @_go('nodeid_right')
