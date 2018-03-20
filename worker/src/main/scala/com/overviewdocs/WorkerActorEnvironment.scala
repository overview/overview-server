package com.overviewdocs

import akka.actor.{ActorRef,ActorSystem,UnhandledMessage}
import java.nio.file.{Files,Path}

import com.overviewdocs.akkautil.BrokerActor
import com.overviewdocs.background.filecleanup.{ DeletedFileCleaner, FileCleaner, FileRemovalRequestQueue }
import com.overviewdocs.background.filegroupcleanup.{ DeletedFileGroupCleaner, FileGroupCleaner, FileGroupRemovalRequestQueue }
import com.overviewdocs.background.reindex.ReindexActor
import com.overviewdocs.clone.Cloner
import com.overviewdocs.database.{Database,DocumentSetDeleter}
import com.overviewdocs.jobhandler.documentset.{DocumentSetCommandWorker,DocumentSetMessageBroker,SortRunner,SortWorker,SortBroker}
import com.overviewdocs.jobhandler.csv.{CsvImportWorkBroker,CsvImportWorker}
import com.overviewdocs.jobhandler.documentcloud.{DocumentCloudImportWorkBroker,DocumentCloudImportWorker}
import com.overviewdocs.jobhandler.filegroup._
import com.overviewdocs.searchindex.Indexer
import com.overviewdocs.sort.SortConfig
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.{Configuration,Logger}

class WorkerActorEnvironment(database: Database, tempDirectory: Path) {
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

  private val csvImportWorkBroker = system.actorOf(CsvImportWorkBroker.props, "CsvImportWorkBroker")
  private val documentCloudImportWorkBroker = system.actorOf(DocumentCloudImportWorkBroker.props, "DocumentCloudImportWorkBroker")
  private val indexer = system.actorOf(Indexer.props, "Indexer")
  private val reindexer = system.actorOf(ReindexActor.props, "ReindexActor")

  system.actorOf(CsvImportWorker.props(csvImportWorkBroker), "CsvImportWorker-1")
  system.actorOf(DocumentCloudImportWorker.props(documentCloudImportWorkBroker), "DocumentCloudWorker-1")

  private val sortRunner = new SortRunner(database, 100, SortConfig(
    tempDirectory=Files.createDirectory(tempDirectory.resolve("sort")),
    maxNBytesInMemory=1024 * 1024 * Configuration.getInt("max_mb_per_sort")
  ))
  val sortBroker = system.actorOf(SortBroker.props)
  Seq.tabulate(Configuration.getInt("n_concurrent_sorts")) { i =>
    system.actorOf(SortWorker.props(sortBroker, sortRunner), "SortWorker-" + i)
  }

  val progressReporter = system.actorOf(ProgressReporter.props)

  val fileGroupImportMonitor = FileGroupImportMonitor.withProgressReporter(system, progressReporter)
  val complete = fileGroupImportMonitor.run

  system.actorOf(
    DocumentSetCommandWorker.props(
      messageBroker,
      fileGroupImportMonitor,
      csvImportWorkBroker,
      documentCloudImportWorkBroker,
      indexer,
      reindexer,
      sortBroker,
      Cloner,
      DocumentSetDeleter
  ), "DocumentSetCommandWorker")
}
