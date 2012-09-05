DocumentStore = require('models/document_store').DocumentStore
OnDemandTree = require('models/on_demand_tree').OnDemandTree
TagStore = require('models/tag_store').TagStore
NeedsResolver = require('models/needs_resolver').NeedsResolver
TransactionQueue = require('models/transaction_queue').TransactionQueue

class Cache
  constructor: () ->
    @document_store = new DocumentStore()
    @tag_store = new TagStore()
    @needs_resolver = new NeedsResolver(@document_store, @tag_store)
    @server = @needs_resolver.server
    @on_demand_tree = new OnDemandTree(@needs_resolver)
    @transaction_queue = new TransactionQueue()

  load_root: () ->
    @on_demand_tree.demand_root()

exports = require.make_export_object('models/cache')
exports.Cache = Cache
