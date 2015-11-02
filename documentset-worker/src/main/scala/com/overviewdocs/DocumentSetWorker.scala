package com.overviewdocs

import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import com.overviewdocs.background.filecleanup.{ DeletedFileCleaner, FileCleaner, FileRemovalRequestQueue }
import com.overviewdocs.background.filegroupcleanup.{ DeletedFileGroupCleaner, FileGroupCleaner, FileGroupRemovalRequestQueue }
import com.overviewdocs.database.{DB,DocumentSetDeleter,HasDatabase}
import com.overviewdocs.jobhandler.documentset.{DocumentSetCommandWorker,DocumentSetMessageBroker}
import com.overviewdocs.jobhandler.filegroup._
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.tables.FileGroups
import com.overviewdocs.util.Logger

/** Main app: starts up actors and listens for messages.
  */
object DocumentSetWorker extends App {
  private val WorkerActorSystemName = "WorkerActorSystem"
  private val FileGroupJobQueueName = "FileGroupJobQueue"
  private val FileRemovalQueueName = "FileRemovalQueue"

  // Connect to the database
  DB.dataSource

  implicit val system = ActorSystem(WorkerActorSystemName)
  val actorCareTaker = system.actorOf(Props(classOf[ActorCareTaker], FileGroupJobQueueName, FileRemovalQueueName), "supervised")
}

/**
 * Supervisor for the actors.
 * Creates the connection hosting the message queues, and tells
 * clients to register for connection status messages.
 * If an error occurs at this level, we assume that something catastrophic has occurred.
 * All actors get killed, and the process exits.
 */
class ActorCareTaker(fileGroupJobQueueName: String, fileRemovalQueueName: String) extends Actor with HasDatabase {
  private val logger = Logger.forClass(getClass)

  // FileGroup removal background worker
  private val fileGroupCleaner = createMonitoredActor(FileGroupCleaner(), "FileGroupCleaner")
  private val fileGroupRemovalRequestQueue = createMonitoredActor(FileGroupRemovalRequestQueue(fileGroupCleaner), "FileGroupRemovalRequestQueue")

  // deletedFileGroupRemover is not monitored, because it requests removals for
  // deleted FileGroups on startup, and then terminates.
  private val deletedFileGroupRemover = {
    context.actorOf(DeletedFileGroupCleaner(fileGroupRemovalRequestQueue), "DeletedFileGroupRemover")
  }

  // File removal background worker      
  private val fileCleaner = createMonitoredActor(FileCleaner(), "FileCleaner")
  private val deletedFileRemover = createMonitoredActor(DeletedFileCleaner(fileCleaner), "DeletedFileCleaner")
  private val fileRemovalQueue = createMonitoredActor(FileRemovalRequestQueue(deletedFileRemover), fileRemovalQueueName)

  // DocumentSetCommandWorker
  //
  // [2015-10-06] XXX We can only support one worker
  //
  // Right now, workers only do database commands and the database isn't
  // sharded; one worker makes sense. Before increasing to multiple workers, be
  // sure to implement actual brokering in DocumentSetMessageBroker. Right now,
  // it won't serialize commands as it should.
  private val documentSetMessageBroker = createMonitoredActor(DocumentSetMessageBroker.props, "DocumentSetMessageBroker")
  logger.info("Message broker path: {}", documentSetMessageBroker.toString)

  private val progressReporter = createMonitoredActor(ProgressReporter(), "ProgressReporter")
  private val documentIdSupplier = createMonitoredActor(DocumentIdSupplier(), "DocumentIdSupplier")

  private val addDocumentsWorkBroker = createMonitoredActor(AddDocumentsWorkBroker.props, "AddDocumentsWorkBroker")
  private val addDocumentsImpl = new AddDocumentsImpl(documentIdSupplier)
  createMonitoredActor(AddDocumentsWorker.props(addDocumentsWorkBroker, addDocumentsImpl), "AddDocumentsWorker-1")
  createMonitoredActor(AddDocumentsWorker.props(addDocumentsWorkBroker, addDocumentsImpl), "AddDocumentsWorker-2")

  createMonitoredActor(
    DocumentSetCommandWorker.props(documentSetMessageBroker, addDocumentsWorkBroker, DocumentSetDeleter),
    "DocumentSetCommandWorker"
  )

  override def preStart: Unit = {
    resumeAddDocumentsCommands(context.dispatcher)
  }

  /** Error? Die. On production, that will trigger restart. */
  override def supervisorStrategy = AllForOneStrategy(0, Duration.Inf) {
    case _ => stop
  }

  def receive = {
    case Terminated(a) => {
      logger.error("Unexpected shutdown")
      context.system.shutdown
      System.exit(-1)
    }
  }

  private def createMonitoredActor(props: Props, name: String): ActorRef = {
    val monitee = context.actorOf(props, name) // like manatee? get it?
    context.watch(monitee)
  }

  private def resumeAddDocumentsCommands(implicit ec: ExecutionContext): Unit = {
    import database.api._

    database.seq(FileGroups.filter(_.addToDocumentSetId.nonEmpty)).map(_.foreach { fileGroup =>
      val command = DocumentSetCommands.AddDocumentsFromFileGroup(fileGroup)
      logger.info("Resuming {}...", command)
      documentSetMessageBroker ! command
    })
  }
}
