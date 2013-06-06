define [
  'jquery'
  './document_store'
  './on_demand_tree'
  './tag_store'
  './TagLikeApi'
  './search_result_store'
  './needs_resolver'
  './transaction_queue'
], ($, DocumentStore, OnDemandTree, TagStore, TagLikeApi, SearchResultStore, NeedsResolver, TransactionQueue) ->
  Deferred = $.Deferred

  # A Cache stores documents, nodes and tags, plus a transaction queue.
  #
  # Nodes, documents and tags are plain old data objects, stored in
  # @document_store, @tag_store and @on_demand_tree.
  #
  # @transaction_queue can be used to fetch data from the server and
  # send modifications to the server.
  #
  # * add_*, edit_*, remove_*: Add or remove objects from their appropriate
  #   stores. (The methods are here when removals cross boundaries: for example,
  #   removing a tag means all documents must be modified.)
  # * create_*, update_*, delete_*: Update the cache, *and* queue a transaction to
  #   modify the server's data.
  #
  # Lots of these methods are inconsistent and ugly. This whole class needs
  # rethinking and splitting-up.
  class Cache
    constructor: () ->
      @document_store = new DocumentStore()
      @tag_store = new TagStore()
      @search_result_store = new SearchResultStore("#{window.location.pathname}/search-results")
      @needs_resolver = new NeedsResolver(@document_store, @tag_store, @search_result_store)
      @transaction_queue = new TransactionQueue()
      @server = @needs_resolver.server
      @tag_api = new TagLikeApi(@tag_store, @transaction_queue, "#{window.location.pathname}/tags")
      @search_result_api = new TagLikeApi(@search_result_store, @transaction_queue, "#{window.location.pathname}/searches")
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

    # Requests new node counts from the server, and updates the cache
    refresh_tagcounts: (tag) ->
      nodes = @on_demand_tree.nodes
      node_ids = _(nodes).keys()
      node_ids_string = node_ids.join(',')
      deferred = @server.post('tag_node_counts', { nodes: node_ids_string }, { path_argument: tag.id })
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

    create_tag: (tag, options) ->
      @tag_api.create(tag, options)

    add_tag: (attributes) ->
      @tag_store.add(attributes)

    edit_tag: (tag, new_tag) ->
      @tag_store.change(tag, new_tag)

    update_tag: (tag, new_tag) ->
      this.edit_tag(tag, new_tag)
      @tag_api.update(tag, new_tag)

    remove_tag: (tag) ->
      @document_store.remove_tag_id(tag.id)

      tagid_string = "#{tag.id}"
      nodes = @on_demand_tree.nodes
      @on_demand_tree.id_tree.edit ->
        for __, node of nodes
          tagcounts = node.tagcounts
          if tagcounts?[tagid_string]?
            delete tagcounts[tagid_string]

        undefined

      @tag_store.remove(tag)

    delete_tag: (tag) ->
      @remove_tag(tag)
      @tag_api.destroy(tag)

    edit_node: (node, new_node) ->
      @on_demand_tree.id_tree.edit ->
        for k, v of new_node
          if !v?
            node[k] = undefined
          else
            node[k] = JSON.parse(JSON.stringify(v))

    update_node: (node, new_node) ->
      id = node.id

      this.edit_node(node, new_node)

      @transaction_queue.queue =>
        @server.post('node_update', new_node, { path_argument: id })
