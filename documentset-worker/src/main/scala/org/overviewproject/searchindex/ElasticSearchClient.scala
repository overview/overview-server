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
  private val ConfigFile = Configuration.searchIndex.getString("config_file")
  private val SearchIndexHost = Configuration.searchIndex.getString("host")
  private val SearchIndexPort = Configuration.searchIndex.getInt("port")

  val client: TransportClient = createTransportClient

  private def createTransportClient: TransportClient = {
    val settings = ImmutableSettings.settingsBuilder.loadFromClasspath(ConfigFile)
    new TransportClient(settings)

    Logger.info(s"Connecting to Search Index [${settings.get("cluster.name")}] at $SearchIndexHost:$SearchIndexPort")

    val transportClient = new TransportClient(settings)
    transportClient.addTransportAddress(new InetSocketTransportAddress(SearchIndexHost, SearchIndexPort))
  }

}