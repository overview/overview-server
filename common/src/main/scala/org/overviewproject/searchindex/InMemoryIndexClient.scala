package org.overviewproject.searchindex

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.apache.commons.io.FileUtils
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.{Node,NodeBuilder}
import scala.concurrent.Future

import org.overviewproject.tree.orm.Document // FIXME should be model

/** A self-contained search index.
  *
  * This is suitable for running tests that rely on ElasticSearch. But it's
  * also suitable for testing ElasticSearchIndexClient, so we've put it in the
  * `common` project, instead of `common-test`.
  */
class InMemoryIndexClient extends ElasticSearchIndexClient {
  // ElasticSearch nodes will find one another within the same JVM. Make them
  // unique.
  val clusterName = UUID.randomUUID().toString()
  val path = Files.createTempDirectory(s"in-memory-index-client-${clusterName}").toString

  def publicClientFuture = clientFuture

  private lazy val node: Node = {
    val settings = ImmutableSettings.settingsBuilder
      .put("index.store.type", "memory")
      .put("index.number_of_shards", 1)
      .put("index.number_of_replicas", 0)
      .put("node.http.enabled", false)
      .put("node.gateway.type", "none")
      .put("path.logs", s"$path/logs")
      .put("path.data", s"$path/data")

    NodeBuilder.nodeBuilder()
      .clusterName(clusterName)
      .local(true)
      .data(true)
      .settings(settings)
      .node()
  }

  override def connect = {
    Future.successful(node.client())
  }

  override def disconnect = {
    node.close()
    FileUtils.deleteDirectory(new File(path))
    Future.successful(Unit)
  }
}
