NodeFormView = require('views/node_form_view').NodeFormView

get_default_log = () -> require('globals').logger.for_component('node_form')

node_to_short_string = (node) ->
  "#{node.id} (#{node.description})"

node_diff_to_string = (node1, node2) ->
  changed = false
  s = ''
  if node1.description != node2.description
    s += " description: <<#{node1.description}>> to <<#{node2.description}>>"
    changed = true
  if !changed
    s += " (no change)"
  s

# Pops up a modal dialog to modify/delete a node
#
# Handles logging and hiding the dialog. Just call and forget.
#
# TODO: merge code (and tests) with tag_form_controller
node_form_controller = (node, cache, state, options=undefined) ->
  log = options?.log || get_default_log()

  log('began editing node', node_to_short_string(node))

  form = options?.create_form?(node) || new NodeFormView(node)

  form.observe 'closed', ->
    log('stopped editing node', node_to_short_string(node))
    form = undefined

  form.observe 'change', (new_node) ->
    log('edited node', "#{node.id}:#{node_diff_to_string(node, new_node)}")
    cache.update_node(node, new_node)

  undefined

exports = require.make_export_object('controllers/node_form_controller')
exports.node_form_controller = node_form_controller
