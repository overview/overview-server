package org.overviewproject.util

import com.typesafe.config.{ Config, ConfigFactory }


class WorkerConfiguration(val configuration: Config) extends DefaultConfigurable {
  val prefix: String = ""
    
  private val MaxDocuments: String = "max_documents"
  private val MaxInFlightRequests: String = "max_inflight_requests"
  private val PageSize: String = "page_size"


  val defaultValues: Map[String, Any] = Map(
    (MaxDocuments -> 50000),
    (PageSize -> 50),
    (MaxInFlightRequests -> 4))
    
  val maxDocuments: Int = getInt(MaxDocuments)
  val pageSize: Int = getInt(PageSize)
  val maxInFlightRequests: Int = getInt(MaxInFlightRequests)
      
}

class MessageQueueConfiguration(val configuration: Config) extends DefaultConfigurable {
  val prefix: String = "message_queue"
  private val BrokerUri = "broker_uri"
  private val QueueName = "queue_name"
  private val Username: String = "admin"
  private val Password: String = "password"
   
  val defaultValues: Map[String, Any] = Map(
    (BrokerUri -> "tcp://localhost:61613"),
    (Username -> "admin"),
    (Password -> "password"),
    (QueueName -> "/queue/document-set-commands")
  )
  
  val brokerUri: String = getString(BrokerUri)
  val username: String = getString(Username)
  val password: String = getString(Password)
  val queueName: String = getString(QueueName)
}

object Configuration {
  private val configuration: Config = ConfigFactory.load()
  private val globalConfig = new WorkerConfiguration(configuration)
  
  val maxDocuments = globalConfig.maxDocuments
  val pageSize = globalConfig.pageSize
  val maxInFlightRequests = globalConfig.maxInFlightRequests
  
  val messageQueue = new MessageQueueConfiguration(configuration)    

}