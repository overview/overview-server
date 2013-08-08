package org.overviewproject
import scala.concurrent.duration._

import akka.actor._

import scala.language.postfixOps

import org.overviewproject.database.DB
import org.overviewproject.database.DataSource
import org.overviewproject.database.SystemPropertiesDatabaseConfiguration
import org.overviewproject.http.{AsyncHttpClientWrapper, RequestQueue}
import org.overviewproject.jobhandler.JobHandler
import org.overviewproject.jobhandler.JobHandlerProtocol.StartListening


/**
 * Creates as many JobHandler actors as we think we can handle, with a shared
 * RequestQueue.
 * Each JobHandler is responsible for listening to the message queue, and spawning
 * handlers for incoming commands.
 * We assume that the queue is setup with message groups, so that messages for a 
 * particular document set are sent to one JobHandler only. At some point JobHandlers
 * may want to explicitly disconnect from the queue, to rebalance the message groups.
 */
object DocumentSetWorker extends App {
   val config = new SystemPropertiesDatabaseConfiguration()
  val dataSource = new DataSource(config)

    DB.connect(dataSource)

  val client = new AsyncHttpClientWrapper
  
  val system = ActorSystem("WorkerActorSystem")
  val requestQueue = system.actorOf(Props(new RequestQueue(client, 4, 6 minutes)))
  val jobHandler = system.actorOf(JobHandler(requestQueue))

  // Start as many job handlers as you need
  jobHandler ! StartListening

  

}