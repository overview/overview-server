package org.overviewproject

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.database.{ DataSource, DB }
import org.overviewproject.database.SystemPropertiesDatabaseConfiguration
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandler
import org.overviewproject.jobhandler.filegroup.ClusteringJobHandler
import org.overviewproject.util.Logger
import org.overviewproject.messagequeue.MessageQueueConnection
import org.overviewproject.messagequeue.MessageQueueConnectionProtocol._
import org.overviewproject.messagequeue.AcknowledgingMessageReceiverProtocol._
import org.overviewproject.messagequeue.apollo.ApolloMessageQueueConnection
import ActorCareTakerProtocol._
import org.overviewproject.jobhandler.filegroup.ClusteringCommandsMessageQueueBridge
import org.overviewproject.jobhandler.filegroup.FileGroupJobManager
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueue
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueue

object ActorCareTakerProtocol {
  case object StartListening
}

/**
 * Creates as many DocumentSetJobHandler actors as we think we can handle, with a shared
 * RequestQueue. DocumentSetJobHandlers handle request specific to DocumentSets, after clustering
 * has started (such as Search and Delete)
 * Each DocumentSetJobHandler is responsible for listening to the message queue, and spawning
 * handlers for incoming commands.
 * We assume that the queue is setup with message groups, so that messages for a
 * particular document set are sent to one JobHandler only. At some point DocumentSetJobHandlers
 * may want to explicitly disconnect from the queue, to rebalance the message groups.
 * Also creates a ClusteringJobHandler which manages processing of file uploads, up until the clustering
 * process starts.
 */
object DocumentSetWorker extends App {
  private val NumberOfJobHandlers = 8
  val config = new SystemPropertiesDatabaseConfiguration()
  val dataSource = new DataSource(config)

  DB.connect(dataSource)

  val system = ActorSystem("WorkerActorSystem")
  val actorCareTaker = system.actorOf(Props(new ActorCareTaker(NumberOfJobHandlers)))
  
  actorCareTaker ! StartListening
}

/**
 * Supervisor for the actors.
 * Creates the connection hosting the message queues, and tells 
 * clients to register for connection status messages.
 * If an error occurs at this level, we assume that something catastrophic has occurred.
 * All actors get killed, and we die.
 */
class ActorCareTaker(numberOfJobHandlers: Int) extends Actor {

  val connectionMonitor = context.actorOf(ApolloMessageQueueConnection())
  // Start as many job handlers as you need
  val jobHandlers = Seq.fill(numberOfJobHandlers)(context.actorOf(DocumentSetJobHandler()))

  val fileGroupJobQueue = context.actorOf(FileGroupJobQueue())
  val clusteringJobQueue = context.actorOf(ClusteringJobQueue())
  val fileGroupJobQueueManager = context.actorOf(FileGroupJobManager(fileGroupJobQueue, clusteringJobQueue))
  val uploadClusteringCommandBridge = context.actorOf(ClusteringCommandsMessageQueueBridge(fileGroupJobQueueManager))

  override def supervisorStrategy = AllForOneStrategy(0, Duration.Inf) {
    case _ => Stop
  }

  def receive = {
    case StartListening => {
      uploadClusteringCommandBridge ! RegisterWith(connectionMonitor)
      jobHandlers.foreach(_ ! RegisterWith(connectionMonitor))
      connectionMonitor ! StartConnection
    }
    case Terminated(a) => {
      Logger.error("Unexpected shutdown")
      context.system.shutdown
    }
  }

}