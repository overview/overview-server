package org.overviewproject.util

import scala.collection.JavaConverters._

import com.typesafe.config.{ Config, ConfigFactory }

trait ConfigurationDefault {
  def path: Option[String]
  def defaultValues: Map[String, Any]
}

trait ConfigurationWithDefaults {
  def defaultConfigs: Iterable[ConfigurationDefault]

  def createWithDefaults: Config = {
    val definedConfiguration = ConfigFactory.load()

    defaultConfigs.foldLeft(definedConfiguration) { (c, d) =>
      val defaults = ConfigFactory.parseMap(d.defaultValues.asJava)
      val defaultsAtPath = d.path.map(defaults.atPath(_)).getOrElse(defaults)

      c.withFallback(defaultsAtPath)
    }
  }
}

object WorkerDefaults extends ConfigurationDefault {
  override def path: Option[String] = None

  val MaxDocuments: String = "max_documents"
  val MaxInFlightRequests: String = "max_inflight_requests"
  val PageSize: String = "page_size"

  override def defaultValues: Map[String, Any] = Map(
    (MaxDocuments -> 50000),
    (PageSize -> 50),
    (MaxInFlightRequests -> 4))
}

object MessageQueueDefaults extends ConfigurationDefault {
  override def path: Option[String] = Some("message_queue")

  val BrokerUri = "broker_uri"
  val QueueName = "queue_name"
  val Username: String = "username"
  val Password: String = "password"

  override def defaultValues: Map[String, Any] = Map(
    (BrokerUri -> "tcp://localhost:61613"),
    (Username -> "admin"),
    (Password -> "password"),
    (QueueName -> "/queue/document-set-commands"))
}

object SearchIndexDefaults extends ConfigurationDefault {
  override def path: Option[String] = Some("search_index")

  val ConfigFile = "config_file"
  val IndexName = "index_name"
    
  override def defaultValues: Map[String, Any] = Map(
    (ConfigFile -> "elasticsearch.yml"),
    (IndexName -> "documents_v1"))
}

class WorkerConfiguration(configuration: Config) {
  import WorkerDefaults._
  
  val maxDocuments: Int = configuration.getInt(MaxDocuments)
  val maxInFlightRequests = configuration.getInt(MaxInFlightRequests)
  val pageSize: Int = configuration.getInt(PageSize)
}

class MessageQueueConfiguration(configuration: Config) {
  import MessageQueueDefaults._
  val prefix: String = "message_queue"

  private val config = configuration.getConfig(prefix) 

  val brokerUri: String = config.getString(BrokerUri)
  val username: String = config.getString(Username)
  val password: String = config.getString(Password)
  val queueName: String = config.getString(QueueName)
}

class SearchIndexConfiguration(configuration: Config) {
  import SearchIndexDefaults._
  
  val prefix: String = "search_index"

  private val config = configuration.getConfig(prefix)

  val configFile: String = config.getString(ConfigFile)
  val indexName: String = config.getString(IndexName)
}

object Configuration extends ConfigurationWithDefaults {
  override def defaultConfigs = Seq(
    WorkerDefaults,
    MessageQueueDefaults,
    SearchIndexDefaults)

  private val configuration: Config = createWithDefaults
  private val globalConfig = new WorkerConfiguration(configuration)
  
  val maxDocuments = globalConfig.maxDocuments
  val pageSize = globalConfig.pageSize
  val maxInFlightRequests = globalConfig.maxInFlightRequests

  val messageQueue = new MessageQueueConfiguration(configuration)
  val searchIndex = new SearchIndexConfiguration(configuration)

}
