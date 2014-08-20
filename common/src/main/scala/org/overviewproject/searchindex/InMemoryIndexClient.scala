package org.overviewproject.searchindex

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.apache.commons.io.FileUtils
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.{Node,NodeBuilder}

import org.overviewproject.tree.orm.Document // FIXME should be model

/** A self-contained search index.
  *
  * This is suitable for running tests that rely on ElasticSearch. But it's
  * also suitable for testing ElasticSearchIndexClient, so we've put it in the
  * `common` project, instead of `common-test`.
  */
class InMemoryIndexClient(val node: Node, override val client: Client, override val indexName: String, val path: String) extends ElasticSearchIndexClient(client, indexName) {
  // Initialize stuff. Synchronous, slow.
  client.admin.indices.prepareCreate(indexName)
    .addMapping(DocumentTypeName, Mapping)
    .setSettings(ImmutableSettings.settingsBuilder
      .put("index.store.type", "memory")
      .put("index.number_of_shards", 1)
      .put("index.number_of_replicas", 0)
    )
    .execute.actionGet

  client.admin.cluster.prepareHealth()
    .setWaitForYellowStatus
    .execute.actionGet

  def close = {
    node.close()
    FileUtils.deleteDirectory(new File(path))
  }
}

object InMemoryIndexClient {
  def apply() = {
    // ElasticSearch nodes will find one another within the same JVM. Make them
    // unique.
    val clusterName = UUID.randomUUID().toString()
    val indexName = "in-memory-index"
    val path = Files.createTempDirectory(s"${indexName}-${clusterName}").toString

    val settings = ImmutableSettings.settingsBuilder
      .put("index.store.type", "memory")
      .put("index.number_of_shards", 1)
      .put("index.number_of_replicas", 0)
      .put("node.http.enabled", false)
      .put("node.gateway.type", "none")
      .put("path.logs", s"$path/logs")
      .put("path.data", s"$path/data")

    val node = NodeBuilder.nodeBuilder()
      .clusterName(clusterName)
      .local(true)
      .data(true)
      .settings(settings)
      .node()

    val client = node.client()

    new InMemoryIndexClient(node, client, indexName, path)
  }
}
