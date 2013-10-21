package org.overviewproject

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.database.{ DataSource, DB }
import org.overviewproject.database.SystemPropertiesDatabaseConfiguration
import org.overviewproject.jobhandler.MessageQueueActorProtocol.StartListening
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandler
import org.overviewproject.jobhandler.filegroup.ClusteringJobHandler
import org.overviewproject.util.Logger

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
  //  val actorCareTaker = system.actorOf(Props(new ActorCareTaker(NumberOfJobHandlers)))
  //
  //  actorCareTaker ! StartListening

  val jobHandlers = Seq.fill(NumberOfJobHandlers)(system.actorOf(DocumentSetJobHandler()))
  
  jobHandlers.foreach(_ ! StartListening)

}

/**
 * Supervisor for the actors.
 * If an error occurs at this level, we assume that something catastrophic has occurred.
 * All actors get killed, and we die.
 */
class ActorCareTaker(numberOfJobHandlers: Int) extends Actor {

  // Start as many job handlers as you need
  val jobHandlers = Seq.fill(numberOfJobHandlers)(context.actorOf(DocumentSetJobHandler()))

  // val clusteringJobHandler = context.actorOf(ClusteringJobHandler())

  override def supervisorStrategy = AllForOneStrategy(0, Duration.Inf) {
    case _ => Stop
  }

  def receive = {
    case StartListening => {
      jobHandlers.foreach(_ ! StartListening)
      // clusteringJobHandler ! StartListening
    }
    case Terminated(a) => {
      Logger.error("Unexpected shutdown")
      context.system.shutdown
    }
  }

}