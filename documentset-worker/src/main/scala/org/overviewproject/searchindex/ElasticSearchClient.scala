package org.overviewproject.searchindex

import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.overviewproject.util.Configuration
import org.elasticsearch.client.transport.TransportClient
import org.overviewproject.util.Logger
import org.elasticsearch.common.transport.InetSocketTransportAddress

/**
 * Keeps a singleton client for ElasticSearch.
 * Configuration is setup in elasticsearch.yml
 */
object ElasticSearchClient {
  private val ClusterName = Configuration.searchIndex.getString("cluster_name")
  private val Hosts = Configuration.searchIndex.getString("hosts")

  val client: NodeIndexClient = new NodeIndexClient(ClusterName, Hosts)
}
