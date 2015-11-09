package com.overviewdocs

import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext,Future}
import scala.language.postfixOps

import com.overviewdocs.background.filecleanup.{ DeletedFileCleaner, FileCleaner, FileRemovalRequestQueue }
import com.overviewdocs.background.filegroupcleanup.{ DeletedFileGroupCleaner, FileGroupCleaner, FileGroupRemovalRequestQueue }
import com.overviewdocs.database.{DB,DocumentSetDeleter,HasDatabase}
import com.overviewdocs.jobhandler.documentset.{DocumentSetCommandWorker,DocumentSetMessageBroker}
import com.overviewdocs.jobhandler.filegroup._
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.tables.{DocumentSets,FileGroups}
import com.overviewdocs.util.Logger

class DocumentSetWorkerKiller extends Actor {
  private val logger = Logger.forClass(getClass)

  override def receive = {
    case UnhandledMessage(message, sender, recipient) => {
      logger.error("Unhandled message from {} to {}: {}", sender, recipient, message)
      if (message.isInstanceOf[Exception]) {
        message.asInstanceOf[Exception].printStackTrace()
      }
      System.exit(-1)
    }
  }
}

object DocumentSetWorkerKiller {
  def props: Props = Props(new DocumentSetWorkerKiller)
}

/** Main app: starts up actors and listens for messages.
  */
object DocumentSetWorker extends App {
  private val WorkerActorSystemName = "WorkerActorSystem"
  private val FileGroupJobQueueName = "FileGroupJobQueue"
  private val FileRemovalQueueName = "FileRemovalQueue"

  // Connect to the database
  DB.dataSource

  val system = ActorSystem(WorkerActorSystemName)
  system.eventStream.subscribe(
    system.actorOf(DocumentSetWorkerKiller.props, "DocumentSetWorkerKiller"),
    classOf[UnhandledMessage]
  )

  val actorCareTaker = system.actorOf(Props(classOf[ActorCareTaker], FileGroupJobQueueName, FileRemovalQueueName), "supervised")
}

class ActorCareTaker(fileGroupJobQueueName: String, fileRemovalQueueName: String) extends Actor with HasDatabase {
  private val logger = Logger.forClass(getClass)

  override def receive = {
    // Really, this class shouldn't exist. It doesn't need any messages.
    case Unit => {}
  }

  // FileGroup removal background worker
  private val fileGroupCleaner = context.actorOf(FileGroupCleaner(), "FileGroupCleaner")
  private val fileGroupRemovalRequestQueue = context.actorOf(FileGroupRemovalRequestQueue(fileGroupCleaner), "FileGroupRemovalRequestQueue")

  // deletedFileGroupRemover is not monitored, because it requests removals for
  // deleted FileGroups on startup, and then terminates.
  private val deletedFileGroupRemover = {
    context.actorOf(DeletedFileGroupCleaner(fileGroupRemovalRequestQueue), "DeletedFileGroupRemover")
  }

  // File removal background worker      
  private val fileCleaner = context.actorOf(FileCleaner(), "FileCleaner")
  private val deletedFileRemover = context.actorOf(DeletedFileCleaner(fileCleaner), "DeletedFileCleaner")
  private val fileRemovalQueue = context.actorOf(FileRemovalRequestQueue(deletedFileRemover), fileRemovalQueueName)

  // DocumentSetCommandWorker
  //
  // [2015-10-06] XXX We can only support one worker
  //
  // Right now, workers only do database commands and the database isn't
  // sharded; one worker makes sense. Before increasing to multiple workers, be
  // sure to implement actual brokering in DocumentSetMessageBroker. Right now,
  // it won't serialize commands as it should.
  private val documentSetMessageBroker = context.actorOf(DocumentSetMessageBroker.props, "DocumentSetMessageBroker")
  logger.info("Message broker path: {}", documentSetMessageBroker.toString)

  private val documentIdSupplier = context.actorOf(DocumentIdSupplier(), "DocumentIdSupplier")
  private val addDocumentsImpl = new AddDocumentsImpl(documentIdSupplier)
  private val progressReporter = context.actorOf(ProgressReporter.props(addDocumentsImpl), "ProgressReporter")

  private val addDocumentsWorkBroker = context.actorOf(
    AddDocumentsWorkBroker.props(progressReporter),
    "AddDocumentsWorkBroker"
  )

  context.actorOf(AddDocumentsWorker.props(addDocumentsWorkBroker, addDocumentsImpl), "AddDocumentsWorker-1")
  context.actorOf(AddDocumentsWorker.props(addDocumentsWorkBroker, addDocumentsImpl), "AddDocumentsWorker-2")

  context.actorOf(
    DocumentSetCommandWorker.props(documentSetMessageBroker, addDocumentsWorkBroker, DocumentSetDeleter),
    "DocumentSetCommandWorker"
  )

  override def preStart: Unit = {
    resumeCommands(context.dispatcher)
  }

  /** Infers Commands from the database and sends them to documentSetMessageBroker
    */
  private def resumeCommands(implicit ec: ExecutionContext): Unit = {
    for {
      _ <- resumeAddDocumentsCommands
      _ <- resumeDeleteDocumentSetCommands // We add first, because an add, cancelled, nixes a GroupedFileUpload
    } yield ()
  }

  private def resumeAddDocumentsCommands(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    val q = FileGroups
      .filter(_.addToDocumentSetId.nonEmpty)
      //.filter(_.deleted === false) // there's work to do even for deleted file groups

    database.seq(q).map(_.foreach { fileGroup =>
      val command = DocumentSetCommands.AddDocumentsFromFileGroup(fileGroup)
      logger.info("Resuming {}...", command)
      documentSetMessageBroker ! command
    })
  }

  private def resumeDeleteDocumentSetCommands(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    database.seq(DocumentSets.filter(_.deleted).map(_.id)).map(_.foreach { id =>
      val command = DocumentSetCommands.DeleteDocumentSet(id)
      logger.info("Resuming {}...", command)
      documentSetMessageBroker ! command
    })
  }
}
