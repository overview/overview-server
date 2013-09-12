package org.overviewproject

import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor._
import org.overviewproject.database.{ DataSource, DB }
import org.overviewproject.database.SystemPropertiesDatabaseConfiguration
import org.overviewproject.http.{ AsyncHttpClientWrapper, RequestQueue }
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandler
import org.overviewproject.jobhandler.MessageQueueActorProtocol.StartListening
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandler
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ListenForFileGroupJobs

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
  private val NumberOfJobHandlers = 4
  
  val config = new SystemPropertiesDatabaseConfiguration()
  val dataSource = new DataSource(config)

  DB.connect(dataSource)

  val client = new AsyncHttpClientWrapper

  val system = ActorSystem("WorkerActorSystem")
  val requestQueue = system.actorOf(Props(new RequestQueue(client, 4, 6 minutes)))

  // Start as many job handlers as you need
  val jobHandlers = Seq.fill(NumberOfJobHandlers)(system.actorOf(DocumentSetJobHandler()))
  jobHandlers.foreach { jh =>
    jh ! StartListening
  }

  val fileGroupJobHandler = system.actorOf(FileGroupJobHandler())
  fileGroupJobHandler ! ListenForFileGroupJobs
}

