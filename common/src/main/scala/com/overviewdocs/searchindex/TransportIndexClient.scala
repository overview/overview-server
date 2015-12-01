package com.overviewdocs.searchindex

import java.net.InetSocketAddress
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import scala.concurrent.Future

import com.overviewdocs.util.Configuration

/** An ElasticSearch client that connects as a peer.
  *
  * This is the recommended client.
  *
  * Rather than automatically detect masters, it uses the provided hostnames.
  * Pass a `hosts` string like `"10.0.0.1:9300,10.0.0.2:9300"`, for instance.
  */
class TransportIndexClient(clusterName: String, hosts: String) extends ElasticSearchIndexClient {
  lazy private val transportClient: TransportClient = {
    val settings = Settings.settingsBuilder.put("cluster.name", clusterName)

    val ret = TransportClient.builder.settings(settings).build

    hosts
      .trim
      .split(",")
      .map(_.trim)
      .map(_.split(':').toSeq)
      .foreach { (ipAndPort: Seq[String]) =>
        val ip = ipAndPort(0)
        val port = ipAndPort(1).toInt
        val socketAddress = new InetSocketAddress(ip, port)
        ret.addTransportAddress(new InetSocketTransportAddress(socketAddress))
      }

    ret
  }

  override def connect = Future.successful(transportClient)
  override def disconnect = Future.successful(transportClient.close)
}

object TransportIndexClient {
  lazy val singleton = {
    val clusterName = Configuration.getString("search_index.cluster_name")
    val hosts = Configuration.getString("search_index.hosts")

    new TransportIndexClient(clusterName, hosts)
  }
}
