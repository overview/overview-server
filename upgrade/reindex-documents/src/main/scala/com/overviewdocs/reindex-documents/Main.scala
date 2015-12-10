package com.overviewdocs.upgrade.reindex_documents

import play.api.libs.json.{JsValue,Json}

/** Reindex all documents, while Overview is running.
  *
  * Usage:
  *
  * <pre>
  *   reindex-documents                                          \
  *     --database-url "postgres://user:pass@localhost/overview" \
  *     --elasticsearch-url "localhost:9200"                     \
  *     --index-name "documents_v2"                              \
  * </pre>
  *
  * Before doing this, be sure your index has its correct mapping. Ours is in
  * common/src/main/resources/documents-mapping.json.
  *
  * When the command is finished, there will be a new index (named
  * <tt>documents_v2</tt> in this example), and the <tt>documents</tt> alias
  * will point to it. You should delete the old index(es) manually.
  *
  * If the command is interrupted, simply run it again.
  *
  * During operation, both the old and new indexes may contain the same
  * documents. That will only happen in one document set at a time.
  *
  * The logic is in
  * common/src/main/scala/org/overviewproject/searchindex/ElasticSearchIndexClient.scala
  */
object Main extends App {
  case class Config(
    databaseUrl: PostgresUrl = PostgresUrl("", None, "", Map()),
    elasticsearchUrl: ElasticSearchUrl = ElasticSearchUrl("", -1),
    elasticsearchCluster: String = "",
    indexName: String = ""
  )

  import PostgresUrl.postgresUrlRead
  import ElasticSearchUrl.elasticSearchUrlRead

  val parser = new scopt.OptionParser[Config]("reindex-documents") {
    head("reindex-documents", "0.x")

    opt[PostgresUrl]("database-url")
      .action { (x, c) => c.copy(databaseUrl = x) }
      .text("Postgres URL like \"postgresql://localhost/overview?user=fred&password=secret&ssl=true\"")
      .required()

    opt[ElasticSearchUrl]("elasticsearch-url")
      .action { (x, c) => c.copy(elasticsearchUrl = x) }
      .text("ElasticSearch HTTP URL like \"localhost:9200\"")
      .required()

    opt[String]("index-name")
      .action { (x, c) => c.copy(indexName = x) }
      .text("New index name, such as \"documents_v2\"")
      .required()
  }

  val config = parser.parse(args, Config()).get

  val database = new Database(config.databaseUrl)
  val reindexer = new Reindexer(config.elasticsearchUrl, config.elasticsearchCluster, config.indexName)

  reindexer.addDocumentSetAliases(database)
  reindexer.updateDocumentsAlias
  reindexer.reindexAllDocumentSets(database)

  reindexer.close
  database.close
}
