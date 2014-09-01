package org.overviewproject.searchindex

import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.{Node,NodeBuilder}
import scala.concurrent.Future

import org.overviewproject.util.Configuration

/** An ElasticSearch client that connects as a peer.
  *
  * This is the recommended client.
  *
  * Rather than automatically detect masters, it uses the provided hostnames.
  * Pass a `hosts` string like `"10.0.0.1:9300,10.0.0.2:9300"`, for instance.
  */
class NodeIndexClient(clusterName: String, hosts: String) extends ElasticSearchIndexClient {
  def internalClientFuture: Future[Client] = clientFuture

  lazy private val node: Node = {
    val settings = ImmutableSettings.settingsBuilder
      .put("node.http.enabled", false)
      .put("node.gateway.type", "none")
      .put("discovery.zen.ping.unicast.hosts", hosts)

    NodeBuilder.nodeBuilder()
      .clusterName(clusterName)
      .client(true)
      .settings(settings)
      .node()
  }

  override def connect = {
    Future.successful(node.client())
  }

  override def disconnect = Future.successful(node.close)
}

object NodeIndexClient {
  lazy val singleton = {
    val hosts = Configuration.searchIndex.getString("hosts")
    val clusterName = Configuration.searchIndex.getString("cluster_name")

    new NodeIndexClient(clusterName, hosts)
  }
}
