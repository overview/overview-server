package org.overviewproject.searchindex

import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import scala.concurrent.Future

import org.overviewproject.util.Configuration

/** An ElasticSearch client that connects as a peer.
  *
  * This is the recommended client.
  *
  * Rather than automatically detect masters, it uses the provided hostnames.
  * Pass a `hosts` string like `"10.0.0.1:9300,10.0.0.2:9300"`, for instance.
  */
class TransportIndexClient(clusterName: String, hosts: String) extends ElasticSearchIndexClient {
  def internalClientFuture: Future[Client] = clientFuture

  lazy private val transportClient: TransportClient = {
    val settings = ImmutableSettings.settingsBuilder
      .put("cluster.name", clusterName)

    val ret = new TransportClient(settings)

    hosts
      .trim
      .split(",")
      .map(_.trim)
      .map(_.split(':').toSeq)
      .foreach { (ipAndPort: Seq[String]) =>
        val ip = ipAndPort(0)
        val port = ipAndPort(1).toInt
        ret.addTransportAddress(new InetSocketTransportAddress(ip, port))
      }

    ret
  }

  override def connect = Future.successful(transportClient)
  override def disconnect = Future.successful(transportClient.close)
}

object TransportIndexClient {
  lazy val singleton = {
    val clusterName = Configuration.searchIndex.getString("cluster_name")
    val hosts = Configuration.searchIndex.getString("hosts")

    new TransportIndexClient(clusterName, hosts)
  }
}
