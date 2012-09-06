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
    @transaction_queue = new TransactionQueue()
    @server = @needs_resolver.server
    @on_demand_tree = new OnDemandTree(@needs_resolver)

  load_root: () ->
    @on_demand_tree.demand_root()

  refresh_tagcounts: (tag) ->
    nodes = @on_demand_tree.nodes
    node_ids_string = _(nodes).keys().join(',')
    deferred = @server.post('tag_node_counts', { nodes: node_ids_string }, { path_argument: tag.name })
    deferred.done (data) ->
      i = 0
      tagid = tag.id
      while i < data.length
        nodeid = data[i++]
        count = data[i++]

        tagcounts = nodes[nodeid]?.tagcounts

        if tagcounts?
          if count
            tagcounts[tagid] = count
          else
            delete tagcounts[tagid]

      undefined

exports = require.make_export_object('models/cache')
exports.Cache = Cache
