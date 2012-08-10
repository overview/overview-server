app = require('app')

DocumentStore = require('models/document_store').DocumentStore
TagStore = require('models/tag_store').TagStore
OnDemandTree = require('models/on_demand_tree').OnDemandTree
AnimatedFocus = require('models/animated_focus').AnimatedFocus
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator
TransactionQueue = require('models/transaction_queue').TransactionQueue
RemoteTagList = require('models/remote_tag_list').RemoteTagList

document_store = new DocumentStore()
tag_store = new TagStore()

state = new app.models.State()

server = new app.models.Server()
transaction_queue = new TransactionQueue()

needs_resolver = new app.models.NeedsResolver(document_store, tag_store, server)

log = require('globals').logger

tree = new OnDemandTree(needs_resolver)
tree.demand_root()

interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
animator = new Animator(interpolator)
focus = new AnimatedFocus(animator)

remote_tag_list = new RemoteTagList(tag_store, tree, document_store, transaction_queue, server)

jQuery ($) ->
  log_controller = require('controllers/log_controller').log_controller
  log_controller(log, server)

  $('#focus').each () ->
    focus_controller = require('controllers/focus_controller').focus_controller
    focus_controller(this, focus)
  $('#tree').each () ->
    tree_controller = require('controllers/tree_controller').tree_controller
    tree_controller(this, tree, focus, state.selection)
  $('#document-list').each () ->
    document_list_controller = require('controllers/document_list_controller').document_list_controller
    document_list_controller(this, document_store, needs_resolver, state.selection)
  $('#document').each () ->
    document_contents_controller = require('controllers/document_contents_controller').document_contents_controller
    document_contents_controller(this, state.selection, server.router)
  $('#tag-list').each () ->
    tag_list_controller = require('controllers/tag_list_controller').tag_list_controller
    tag_list_controller(this, remote_tag_list, state.selection)
