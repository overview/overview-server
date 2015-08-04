package com.overviewdocs.searchindex

import com.overviewdocs.util.Configuration

/**
 * Keeps a singleton client for ElasticSearch.
 * Configuration is setup in elasticsearch.yml
 */
object ElasticSearchClient {
  private val ClusterName = Configuration.searchIndex.getString("cluster_name")
  private val Hosts = Configuration.searchIndex.getString("hosts")

  val client: TransportIndexClient = new TransportIndexClient(ClusterName, Hosts)
}
