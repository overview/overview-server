define [
  'backbone'
  '../views/node_form_view'
], (Backbone, NodeFormView) ->
  # Pops up a modal dialog to modify/delete a node
  #
  # FIXME move to TreeApp
  #
  # Handles logging and hiding the dialog. Just call and forget.
  node_form_controller = (node, state, options=undefined) ->
    form = options?.create_form?(node) || new NodeFormView(node)

    form.observe 'closed', ->
      form = undefined

    form.observe 'change', (attrs) ->
      onDemandTree = state.get('vizApp')?.onDemandTree
      onDemandTree?.saveNode(node, attrs)

    undefined
