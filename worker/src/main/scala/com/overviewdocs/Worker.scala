package com.overviewdocs

import akka.actor.ActorRef
import java.util.TimeZone
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.{DanglingNodeDeleter,DanglingCommandFinder,DB}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.Logger

object Worker extends App {
  private val logger = Logger.forClass(getClass)

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  jobhandler.filegroup.task.TempDirectory.create // wipe temp dir
  DB.dataSource // connect to the database

  // Start an ActorSystem so we'll have non-daemon threads running.
  val broker = startMainActorSystem

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

  private def cleanDanglingReferences: Future[Unit] = DanglingNodeDeleter.run
  private def loadDanglingCommands: Future[Seq[DocumentSetCommands.Command]] = DanglingCommandFinder.run

  private def startTreeThread: Unit = {
    logger.info("Scanning for Tree jobs...")
    val thread = new Thread(new TreeWorker, "TreeWorker")
    thread.setDaemon(true)
    thread.start
  }

  private def startMainActorSystem: ActorRef = {
    logger.info("Starting actor system...")
    new WorkerActorEnvironment().messageBroker
  }
}
