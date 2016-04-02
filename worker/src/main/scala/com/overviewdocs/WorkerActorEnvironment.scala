package com.overviewdocs

import akka.actor.{ActorRef,ActorSystem,UnhandledMessage}

import com.overviewdocs.background.filecleanup.{ DeletedFileCleaner, FileCleaner, FileRemovalRequestQueue }
import com.overviewdocs.background.filegroupcleanup.{ DeletedFileGroupCleaner, FileGroupCleaner, FileGroupRemovalRequestQueue }
import com.overviewdocs.clone.Cloner
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.documentset.{DocumentSetCommandWorker,DocumentSetMessageBroker}
import com.overviewdocs.jobhandler.csv.{CsvImportWorkBroker,CsvImportWorker}
import com.overviewdocs.jobhandler.documentcloud.{DocumentCloudImportWorkBroker,DocumentCloudImportWorker}
import com.overviewdocs.jobhandler.filegroup._
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.{Configuration,Logger}

class WorkerActorEnvironment {
  private val logger = Logger.forClass(getClass)

  val system = ActorSystem("worker")

  val messageBroker = system.actorOf(DocumentSetMessageBroker.props, "DocumentSetMessageBroker")
  logger.info("Message broker path: {}", messageBroker.toString)

  system.eventStream.subscribe(
    system.actorOf(UnhandledMessageHandler.props, "UnhandledMessageHandler"),
    classOf[UnhandledMessage]
  )

  // FileGroup removal background worker
  private val fileGroupCleaner = system.actorOf(FileGroupCleaner(), "FileGroupCleaner")
  private val fileGroupRemovalRequestQueue = system.actorOf(FileGroupRemovalRequestQueue(fileGroupCleaner), "FileGroupRemovalRequestQueue")

  // deletedFileGroupRemover is not monitored, because it requests removals for
  // deleted FileGroups on startup, and then terminates.
  private val deletedFileGroupRemover = {
    system.actorOf(DeletedFileGroupCleaner(fileGroupRemovalRequestQueue), "DeletedFileGroupRemover")
  }

  // File removal background worker      
  private val fileCleaner = system.actorOf(FileCleaner(), "FileCleaner")
  private val deletedFileRemover = system.actorOf(DeletedFileCleaner(fileCleaner), "DeletedFileCleaner")
  private val fileRemovalQueue = system.actorOf(FileRemovalRequestQueue(deletedFileRemover), "FileRemovalQueue")

  private val documentIdSupplier = system.actorOf(DocumentIdSupplier(), "DocumentIdSupplier")
  private val addDocumentsImpl = new AddDocumentsImpl(documentIdSupplier)
  private val progressReporter = system.actorOf(ProgressReporter.props(addDocumentsImpl), "ProgressReporter")

  private val addDocumentsWorkBroker = system.actorOf(
    AddDocumentsWorkBroker.props(progressReporter),
    "AddDocumentsWorkBroker"
  )

  private val csvImportWorkBroker = system.actorOf(CsvImportWorkBroker.props, "CsvImportWorkBroker")
  private val documentCloudImportWorkBroker = system.actorOf(DocumentCloudImportWorkBroker.props, "DocumentCloudImportWorkBroker")

  Seq.tabulate(Configuration.getInt("n_document_converters")) { i =>
    val name = "AddDocumentsWorker-" + i
    system.actorOf(AddDocumentsWorker.props(addDocumentsWorkBroker, addDocumentsImpl), name)
  }

  system.actorOf(CsvImportWorker.props(csvImportWorkBroker), "CsvImportWorker-1")
  system.actorOf(DocumentCloudImportWorker.props(documentCloudImportWorkBroker), "DocumentCloudWorker-1")

  system.actorOf(
    DocumentSetCommandWorker.props(
      messageBroker,
      addDocumentsWorkBroker,
      csvImportWorkBroker,
      documentCloudImportWorkBroker,
      Cloner,
      DocumentSetDeleter
    ),
    "DocumentSetCommandWorker"
  )
}
