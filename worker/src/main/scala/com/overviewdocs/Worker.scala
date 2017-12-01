package com.overviewdocs

import akka.actor.ActorRef
import java.util.TimeZone
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.jobhandler.filegroup.task.TempDirectory
import com.overviewdocs.database.{DanglingNodeDeleter,DanglingCommandFinder,Database}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.Logger

object Worker {
  private val logger = Logger.forClass(getClass)

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  TempDirectory.create // wipe temp dir

  private def cleanDanglingReferences: Future[Unit] = DanglingNodeDeleter.run
  private def loadDanglingCommands: Future[Seq[DocumentSetCommands.Command]] = DanglingCommandFinder.run

  private def startTreeThread: Unit = {
    logger.info("Scanning for Tree jobs...")
    val thread = new Thread(new TreeWorker, "TreeWorker")
    thread.setDaemon(true)
    thread.start
  }

  private def startMainActorSystem(database: Database): ActorRef = {
    logger.info("Starting actor system...")
    new WorkerActorEnvironment(database, TempDirectory.path).messageBroker
  }

  def main(args: Array[String]): Unit = {
    // Start an ActorSystem so we'll have non-daemon threads running.
    val database = Database()
    val broker = startMainActorSystem(database)

    for {
      _ <- cleanDanglingReferences
      commands <- loadDanglingCommands
    } yield {
      startTreeThread

      commands.foreach { command =>
        logger.info("Resuming {}...", command)
        broker ! command
      }
    }
  }
}
