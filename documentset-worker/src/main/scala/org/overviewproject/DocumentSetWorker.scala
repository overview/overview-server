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
import org.overviewproject.jobhandler.MessageQueueActorProtocol.RegisterWith
import org.overviewproject.messagequeue.apollo.ApolloMessageQueueConnection

object ActorCareTakerProtocol {
  case object StartListening
}

import ActorCareTakerProtocol._

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

  val system = ActorSystem("WorkerActorSystem")
  val actorCareTaker = system.actorOf(Props(new ActorCareTaker(NumberOfJobHandlers)))
  
  actorCareTaker ! StartListening
}

/**
 * Supervisor for the actors.
 * If an error occurs at this level, we assume that something catastrophic has occurred.
 * All actors get killed, and we die.
 */
class ActorCareTaker(numberOfJobHandlers: Int) extends Actor {

  val connectionMonitor = context.actorOf(ApolloMessageQueueConnection())
  // Start as many job handlers as you need
  val jobHandlers = Seq.fill(numberOfJobHandlers)(context.actorOf(DocumentSetJobHandler()))

  val clusteringJobHandler = context.actorOf(ClusteringJobHandler())

  override def supervisorStrategy = AllForOneStrategy(0, Duration.Inf) {
    case _ => Stop
  }

  def receive = {
    case StartListening => {
      clusteringJobHandler ! RegisterWith(connectionMonitor)
      jobHandlers.foreach(_ ! RegisterWith(connectionMonitor))
      connectionMonitor ! StartConnection
    }
    case Terminated(a) => {
      Logger.error("Unexpected shutdown")
      context.system.shutdown
    }
  }

}