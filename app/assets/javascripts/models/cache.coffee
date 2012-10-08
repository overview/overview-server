DocumentStore = require('models/document_store').DocumentStore
OnDemandTree = require('models/on_demand_tree').OnDemandTree
TagStore = require('models/tag_store').TagStore
NeedsResolver = require('models/needs_resolver').NeedsResolver
TransactionQueue = require('models/transaction_queue').TransactionQueue

Deferred = $.Deferred

class Cache
  constructor: () ->
    @document_store = new DocumentStore()
    @tag_store = new TagStore()
    @needs_resolver = new NeedsResolver(@document_store, @tag_store)
    @transaction_queue = new TransactionQueue()
    @server = @needs_resolver.server
    @on_demand_tree = new OnDemandTree(this) # FIXME this is ugly

  load_root: () ->
    @on_demand_tree.demand_root()

  # Serializes calls to NeedsResolver.get_deferred() through @transaction_queue.
  #
  # FIXME rewrite NeedsResolver & co. This is all very confusing.
  resolve_deferred: () ->
    args = Array.prototype.slice.call(arguments, 0)
    resolver = @needs_resolver

    deferred = new Deferred()

    @transaction_queue.queue ->
      inner_deferred = resolver.get_deferred.apply(resolver, args)
      inner_deferred.done ->
        inner_args = Array.prototype.slice.call(arguments, 0)
        deferred.resolve.apply(deferred, inner_args)
      inner_deferred.fail ->
        inner_args = Array.prototype.slice.call(arguments, 0)
        deferred.reject.apply(deferred, inner_args)

    deferred

  # Removes all references to a tag from the cache
  remove_tag: (tag) ->
    @tag_store.remove(tag)
    @document_store.remove_tag_id(tag.id)

    tagid_string = "#{tag.id}"
    nodes = @on_demand_tree.nodes

    @on_demand_tree.id_tree.edit ->
      for __, node of nodes
        tagcounts = node.tagcounts
        if tagcounts?[tagid_string]?
          delete tagcounts[tagid_string]

      undefined

  # Requests new node counts from the server, and updates the cache
  refresh_tagcounts: (tag) ->
    nodes = @on_demand_tree.nodes
    node_ids = _(nodes).keys()
    node_ids_string = node_ids.join(',')
    deferred = @server.post('tag_node_counts', { nodes: node_ids_string }, { path_argument: tag.name })
    deferred.done (data) =>
      @on_demand_tree.id_tree.edit ->
        tagid = tag.id
        server_tagcounts = {}

        i = 0
        while i < data.length
          nodeid = data[i++]
          count = data[i++]

          server_tagcounts[nodeid] = count

        for nodeid in node_ids
          tagcounts = nodes[nodeid]?.tagcounts
          continue if !tagcounts?

          count = server_tagcounts[nodeid]
          if count
            tagcounts[tagid] = count
          else
            delete tagcounts[tagid]

        undefined

exports = require.make_export_object('models/cache')
exports.Cache = Cache
