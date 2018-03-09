package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef
import akka.stream.{FlowShape,Graph,Materializer}
import akka.stream.scaladsl.{GraphDSL,MergePreferred,Source,Sink}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.database.Database
import com.overviewdocs.ingest.models.{ResumedFileGroupJob,WrittenFile2,ProcessedFile2,IngestedRootFile2,FileGroupProgressState}
import com.overviewdocs.ingest.pipeline.Step
import com.overviewdocs.ingest.{File2Writer,GroupedFileUploadToFile2,Processor,Ingester}
import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.{FileGroups,GroupedFileUploads}
import com.overviewdocs.searchindex.DocumentSetReindexer
import com.overviewdocs.util.{AddDocumentsCommon,Logger}

class FileGroupImportMonitor(
  val database: Database,
  val blobStorage: BlobStorage,
  val documentSetReindexer: DocumentSetReindexer,
  val steps: Vector[Step],
  val progressReporter: ActorRef,
  val maxNTextChars: Int,                // see File2Writer.scala docs
  val nDeciders: Int,                    // see Processor.scala docs
  val recurseBufferSize: Int,            // see Processor.scala docs
  val ingestBatchSize: Int,              // see Ingester.scala docs
  val ingestBatchMaxWait: FiniteDuration // see Ingester.scala docs
)(implicit ec: ExecutionContext, mat: Materializer) {
  private val logger = Logger.forClass(getClass)
  private val inProgress = ArrayBuffer.empty[ResumedFileGroupJob]

  private val fileGroupSource = new FileGroupSource(database, reportProgress _)

  private def reportProgress(progressState: FileGroupProgressState): Unit = {
    progressReporter ! progressState
  }

  def enqueueFileGroup(fileGroup: FileGroup, onComplete: () => Unit): Unit = {
    fileGroupSource.enqueue.tell((fileGroup, onComplete), null)
  }

  private def markFileIngestedInJob(ingested: IngestedRootFile2): ResumedFileGroupJob = {
    val job = ingested.fileGroupJob
    if (job.isComplete) {
      logger.warn("Finishing FileGroup even though it is already complete: {}", job)
    } else {
      job.progressState.incrementNFilesIngested
    }
    job
  }

  private lazy val compiledGroupedFileUploadsForFileGroup = {
    import database.api._
    Compiled { fileGroupId: Rep[Long] =>
      GroupedFileUploads.filter(_.fileGroupId === fileGroupId)
    }
  }

  private lazy val compiledFileGroup = {
    import database.api._
    Compiled { id: Rep[Long] =>
      FileGroups.filter(_.id === id)
    }
  }

  private def addFileGroupFilesToDocumentSetFiles(fileGroupId: Long, documentSetId: Long) = {
    import database.api._
    sqlu"""
      INSERT INTO document_set_file2 (document_set_id, file2_id)
      SELECT ${documentSetId}, file2_id
      FROM grouped_file_upload
      WHERE file_group_id = ${fileGroupId}
        AND NOT EXISTS (SELECT 1 FROM document_set_file2 WHERE document_set_file2.file2_id = grouped_file_upload.file2_id)
    """
  }

  private def finishFileGroupJob(fileGroupJob: ResumedFileGroupJob): Future[Unit] = {
    val documentSetId = fileGroupJob.documentSetId
    val fileGroup = fileGroupJob.fileGroup

    val reindexFuture = documentSetReindexer.reindexDocumentSet(documentSetId) // run in parallel

    for {
      _ <- database.runUnit(addFileGroupFilesToDocumentSetFiles(fileGroup.id, documentSetId))
      _ <- database.delete(compiledGroupedFileUploadsForFileGroup(fileGroup.id))
      _ <- database.delete(compiledFileGroup(fileGroup.id))
      _ <- reindexFuture
      _ <- AddDocumentsCommon.afterAddDocuments(documentSetId)
    } yield {
      fileGroupJob.onComplete()

      // If anything is somehow still waiting for this task to cancel (e.g.,
      // a confused worker long-polling MinimportHttpServer), wake it up.
      //
      // At this point, any data anywhere that refers to this fileGroupJob
      // is spurious; it doesn't matter what programs output, and it doesn't
      // matter what we _input_ into them because it really isn't any of
      // their business whether the user clicked "cancel" or not. They
      // shouldn't be listening.
      //
      // If you've tracked a logic error back to here, don't delete this
      // line: the error is really in your logic. Try something simpler: poll
      // the `cancel.isCompleted` instead of using `cancel.future.onComplete`.
      fileGroupJob.progressState.cancel.trySuccess(akka.Done)
    }
  }

  def cancelFileGroupJob(fileGroupId: Long): Unit = synchronized {
    inProgress
      .filter(_.fileGroup.id == fileGroupId)
      .foreach(job => job.cancel)
  }

  def graph: Graph[FlowShape[ResumedFileGroupJob, IngestedRootFile2], akka.NotUsed] = {
    val groupedFileUploadToFile2 = new GroupedFileUploadToFile2(database, blobStorage)
    val file2Writer = new File2Writer(database, blobStorage, maxNTextChars)

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val resumer = builder.add(new FileGroupFile2Graph(database, groupedFileUploadToFile2).graph)
      val processor = builder.add(new Processor(steps, file2Writer, nDeciders, recurseBufferSize).graph)
      val toIngester = builder.add(MergePreferred[ProcessedFile2](1))
      val ingester = builder.add(Ingester.ingest(file2Writer, ingestBatchSize, ingestBatchMaxWait))

      resumer.out0 ~> processor                           // WrittenFile2s
      resumer.out1 ~>              toIngester.preferred   // ProcessedFile2s: ingester should prefer these
                      processor ~> toIngester ~> ingester // other ProcessedFile2s

      new FlowShape(resumer.in, ingester.out)
    }
  }

  private def addInProgress(job: ResumedFileGroupJob): Unit = synchronized { inProgress.+=(job) }
  private def removeInProgress(job: ResumedFileGroupJob): Unit = synchronized { inProgress.-=(job) }
  private def isInProgress(job: ResumedFileGroupJob): Boolean = synchronized { inProgress.contains(job) }

  def run: Future[Unit] = {
    fileGroupSource.source                     // ResumedFileGroupJob
      .map { job => addInProgress(job); job }
      .via(graph)                              // IngestedFile2Root
      .map(markFileIngestedInJob _)            // ResumedFileGroupJob
      .filter(_.isComplete)                    // Complete ResumedFileGroupJob
      .filter(isInProgress _)                  // avoid a warning
      .map { job => removeInProgress(job); job }
      .mapAsync(1)(finishFileGroupJob _)       // Unit -- now the FileGroup is deleted
      .runReduce((_, _) => ()) // Future[Unit]
  }
}

object FileGroupImportMonitor {
  def withProgressReporter(
    progressReporter: ActorRef
  )(implicit ec: ExecutionContext, mat: Materializer): FileGroupImportMonitor = {
    import com.typesafe.config.ConfigFactory
    val config = ConfigFactory.load

    new FileGroupImportMonitor(
      Database(),
      BlobStorage,
      DocumentSetReindexer,
      com.overviewdocs.ingest.pipeline.Step.All,
      progressReporter,
      config.getInt("max_n_chars_per_document"),
      config.getInt("n_document_converters"),
      config.getInt("max_ingest_recurse_buffer_length"),
      config.getInt("ingest_batch_size"),
      Duration.fromNanos(config.getDuration("ingest_batch_max_wait").toNanos)
    )
  }
}
