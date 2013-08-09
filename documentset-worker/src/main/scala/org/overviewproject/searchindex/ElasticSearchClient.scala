package org.overviewproject.searchindex

import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.overviewproject.util.Configuration

/**
 * Keeps a singleton client for ElasticSearch.
 * Configuration is setup in elasticsearch.yml
 */
object ElasticSearchClient {
  private val ConfigFile = Configuration.searchIndex.configFile
  
  private val node = nodeBuilder.settings(ImmutableSettings.settingsBuilder.loadFromClasspath(ConfigFile)).node
  
  val client = node.client  
}