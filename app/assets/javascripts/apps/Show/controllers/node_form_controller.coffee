define [
  'backbone'
  '../views/node_form_view'
  './logger'
], (Backbone, NodeFormView, Logger) ->
  node_to_short_string = (node) ->
    "#{node.id} (#{node.description})"

  node_diff_to_string = (node, attrs) ->
    changed = false
    s = ''
    if node.description != attrs.description
      s += " description: <<#{node.description}>> to <<#{attrs.description}>>"
      changed = true
    if !changed
      s += " (no change)"
    s

  # Pops up a modal dialog to modify/delete a node
  #
  # FIXME move to TreeApp
  #
  # Handles logging and hiding the dialog. Just call and forget.
  node_form_controller = (node, state, options=undefined) ->
    log = options?.log || Logger.for_component('node_form')

    log('began editing node', node_to_short_string(node))

    form = options?.create_form?(node) || new NodeFormView(node)

    form.observe 'closed', ->
      log('stopped editing node', node_to_short_string(node))
      form = undefined

    form.observe 'change', (attrs) ->
      log('edited node', "#{node.id}:#{node_diff_to_string(node, attrs)}")
      onDemandTree = state.get('vizApp')?.onDemandTree
      onDemandTree?.saveNode(node, attrs)

    undefined
