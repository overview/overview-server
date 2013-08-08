package org.overviewproject.searchindex

import org.elasticsearch.node.NodeBuilder.nodeBuilder

/**
 * Keeps a singleton client for ElasticSearch.
 * Configuration is setup in elasticsearch.yml
 */
object ElasticSearchClient {
  private val node = nodeBuilder.node
  
  val client = node.client  
}