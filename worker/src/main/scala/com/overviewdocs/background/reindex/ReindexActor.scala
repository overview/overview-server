package com.overviewdocs.background.reindex

import akka.stream.ActorMaterializer
import akka.actor.{Actor,Props}
import scala.util.{Failure,Success}

import com.overviewdocs.util.Logger

/** Reindexes [[ReindexJob]]s on startup.
  *
  * When reindexing is done, the actor terminates.
  *
  * Reindexing is necessary after a server upgrade that alters searchindex
  * logic. When that happens, a migration can INSERT a bunch of rows into the
  * ReindexJobs table, and the worker will reindex at the first available
  * opportunity.
  */
class ReindexActor(reindexer: Reindexer) extends Actor {
  import context.dispatcher
  implicit val mat = ActorMaterializer.create(context)

  private val logger = Logger.forClass(getClass)

  override def preStart = clearRunningJobsThenStartReindexing

  override def receive = idle

  def idle: Receive = {
    case ReindexActor.ReindexNextDocumentSet => {
      context.become(reindexing)
      reindexUntilNoJobsRemain
    }
  }

  def reindexing: Receive = {
    case ReindexActor.ReindexNextDocumentSet => {}
  }

  private def clearRunningJobsThenStartReindexing: Unit = {
    reindexer.clearRunningJobs.onComplete {
      case Success(_) => self ! ReindexActor.ReindexNextDocumentSet
      case Failure(ex) => self ! ex
    }
  }

  private def reindexUntilNoJobsRemain: Unit = {
    reindexer.nextJob.onComplete {
      case Success(Some(job)) => {
        logger.info("Reindexing: {}", job)
        reindexer.runJob(job).onComplete {
          case Success(()) => {
            logger.info("Finished reindexing {}", job)
            reindexUntilNoJobsRemain
          }
          case Failure(ex) => self ! ex
        }
      }
      case Success(None) => {
        logger.info("All document set indexes are up to date")
        context.become(idle)
      }
      case Failure(ex) => self ! ex
    }
  }
}

object ReindexActor {
  def props: Props = Props(new ReindexActor(DbLuceneReindexer.singleton))

  case object ReindexNextDocumentSet
}
