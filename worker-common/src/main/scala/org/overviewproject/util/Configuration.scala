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

// Root configuration object reads keys at root level, and points to sub-paths
object Configuration extends ConfigurationKeys(ConfigFactory.load()) {
  override def keys: Map[String, Any] = Map(
    ("max_documents" -> 50000),
    ("page_size" -> 50),
    ("max_inflight_requests" -> 4),
    ("clustering_alg" -> "KMeansComponents"),
    ("min_connected_component_size" -> 10))

  val messageQueue = new MessageQueueConfig(myConfig)
  val searchIndex = new SearchIndexConfig(myConfig)
}
