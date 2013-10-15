/**
 * Configuration.scala
 *
 * Defines keys and default values for worker processes
 *
 * Based on a simple wrapper for Play configuration objects
 *   - add a key with a default in one line of code
 *   - access is type-checked versus type of default value
 *   - supports paths, e.g. foo.bar.key = blah
 * 
 * Overview Project, created October 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.util
import scala.collection.JavaConverters._
import com.typesafe.config.{ Config, ConfigFactory }


// Base class. Subclass and define "keys" to create a configuration object
// Optionally define "path" to root the keys in a sub-path
abstract class ConfigurationKeys(configuration : Config) {
  def path: Option[String] = None
  def keys: Map[String, Any]

  private val defaults = ConfigFactory.parseMap(keys.asJava)

  // Different config object if we are root (empty path) or not
  protected val myConfig =
    if (path.isEmpty) {
      configuration.withFallback(defaults)
    } else { 
      configuration.withFallback(defaults.atPath(path.get)).getConfig(path.get)
    }
          
  def getString(key:String) : String = {
      keys.get(key) match {
      case Some(value:String) => 
        myConfig.getString(key) // default value supplied above
      case None => 
        throw new IllegalArgumentException(s"unknown configuration key $key")
      case Some(s) => 
        val keyType = s.getClass.getName
        throw new IllegalArgumentException(s"caller asked for String but key $key has type $keyType")
    }
  }

  def getInt(key:String) : Int = {
      keys.get(key) match {
      case Some(value:Int) => 
        myConfig.getInt(key)
      case None => 
        throw new IllegalArgumentException(s"unknown configuration key $key")
      case Some(s) => 
        val keyType = s.getClass.getName
        throw new IllegalArgumentException(s"caller asked for Int but key $key has type $keyType")
    }
  }

}

/*trait ConfigurationWithDefaults {
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


// Add default values to the appropriate *Default object
// then add an attribute to the corresponding *Configuration object
object WorkerConfig extends ConfigurationKeys {
  override def keys: Map[String, Any] = Map(
    ("max_documents" -> 50000),
    ("page_size" -> 50),
    ("max_inflight_requests" -> 4),
    ("clustering_alg" -> "KMeansComponents"))
}
*/

class MessageQueueConfig(configuration:Config) extends ConfigurationKeys(configuration) {
  override def path = Some("message_queue")

  override def keys: Map[String, Any] = Map(
    ("broker_uri" -> "tcp://localhost:61613"),
    ("username" -> "admin"),
    ("password" -> "password"),
    ("queue_name" -> "/queue/document-set-commands"),
    ("file_group_queue_name" -> "/queue/file-group-commands"),
    ("clustering_commands_queue_name" -> "/queue/clustering-commands")
  )
}

class SearchIndexConfig(configuration:Config) extends ConfigurationKeys(configuration) {
  override def path = Some("search_index")
    
  override def keys: Map[String, Any] = Map(
    ("config_file" -> "elasticsearch.yml"),
    ("index_name" -> "documents_v1"))
}
/*
class WorkerConfiguration(configuration: Config) {
  import WorkerDefaults._
  
  val maxDocuments: Int = configuration.getInt(MaxDocuments)
  val maxInFlightRequests = configuration.getInt(MaxInFlightRequests)
  val pageSize: Int = configuration.getInt(PageSize)
  val clusteringAlg : String = configuration.getString(ClusteringAlg)
}

class MessageQueueConfiguration(configuration: Config) {
  import MessageQueueDefaults._
  val prefix: String = "message_queue"

  private val config = configuration.getConfig(prefix) 

  val brokerUri: String = config.getString(BrokerUri)
  val username: String = config.getString(Username)
  val password: String = config.getString(Password)
  val queueName: String = config.getString(QueueName)
  val fileGroupQueueName: String = config.getString(FileGroupQueueName)
  val clusteringQueueName: String = config.getString(ClusteringQueueName)
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
  val clusteringAlg = globalConfig.clusteringAlg

  val messageQueue = new MessageQueueConfiguration(configuration)
  val searchIndex = new SearchIndexConfiguration(configuration)

}

*/

// Root configuration object reads keys at root level, and points to sub-paths
object Configuration extends ConfigurationKeys(ConfigFactory.load()) {
  override def keys: Map[String, Any] = Map(
    ("max_documents" -> 50000),
    ("page_size" -> 50),
    ("max_inflight_requests" -> 4),
    ("clustering_alg" -> "KMeansComponents"))

  val messageQueue = new MessageQueueConfig(myConfig)
  val searchIndex = new SearchIndexConfig(myConfig)
}
