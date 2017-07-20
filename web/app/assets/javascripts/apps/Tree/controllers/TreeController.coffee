define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.DocumentListParams')

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

      if _.isEqual(@state.get('documentList')?.params?.params, nodes: [ node.id ])
        @state.set(document: null)
      else
        @_refineStateDocumentListParams(node)

    _onExpand: (node) ->
      @tree.demandNode(node.id)

    _onCollapse: (node) ->
      @tree.unloadNodeChildren(node.id)

      selectedNodeIds = @state.get('documentList')?.params?.params?.nodes || []
      collapsedIsSelected = selectedNodeIds.some((id) => @tree.id_tree.is_id_ancestor_of_id(node.id, id))
      if collapsedIsSelected
        @_refineStateDocumentListParams(node)

    _selectNode: (node) ->
      @_refineStateDocumentListParams(node)
      @tree.demandNode(node.id)

    _refineStateDocumentListParams: (node) ->
      @state.refineDocumentListParams(objects: { nodeIds: [ node.id ], title: t('node', node.description) })

    _findNodeRelativeToSelectedNode: (finder) ->
      nodeId = @state.get('documentList')?.params?.params?.nodes?[0] || null
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
