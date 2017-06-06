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

  private case object Start
  private case object ReindexNextDocumentSet
  private case object NoMoreDocumentSets

  override def preStart = self ! Start

  override def receive = {
    case Start => clearRunningJobs
    case ReindexNextDocumentSet => reindexOrStop
    case NoMoreDocumentSets => context.stop(self)
  }

  private def clearRunningJobs: Unit = {
    reindexer.clearRunningJobs.onComplete {
      case Success(_) => self ! ReindexNextDocumentSet
      case Failure(ex) => self ! ex
    }
  }

  private def reindexOrStop: Unit = {
    reindexer.nextJob.onComplete {
      case Success(Some(job)) => {
        logger.info("Reindexing: {}", job)
        reindexer.runJob(job).onComplete {
          case Success(()) => self ! ReindexNextDocumentSet
          case Failure(ex) => self ! ex
        }
      }
      case Success(None) => {
        logger.info("All document set indexes are up to date")
        self ! NoMoreDocumentSets
      }
      case Failure(ex) => self ! ex
    }
  }
}

object ReindexActor {
  def props: Props = Props(new ReindexActor(DbLuceneReindexer.singleton))
}
