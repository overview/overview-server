package com.overviewdocs.jobhandler.filegroup

import akka.actor.{ActorRef,ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.{FlowShape,Graph,Materializer}
import akka.stream.scaladsl.{Flow,GraphDSL,Keep,Merge,MergePreferred,Partition,Source,Sink}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.database.Database
import com.overviewdocs.ingest.ingest.Ingester
import com.overviewdocs.ingest.model.{ResumedFileGroupJob,WrittenFile2,ProcessedFile2,IngestedRootFile2,FileGroupProgressState}
import com.overviewdocs.ingest.create.GroupedFileUploadToFile2
import com.overviewdocs.ingest.process.{Processor,Step,HttpWorkerServer}
import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.jobhandler.documentset.DocumentSetMessageBroker
import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.{FileGroups,GroupedFileUploads}
import com.overviewdocs.searchindex.DocumentSetReindexer
import com.overviewdocs.util.{AddDocumentsCommon,Logger}

class FileGroupImportMonitor(
  val file2Writer: File2Writer,
  val httpWorkerServer: HttpWorkerServer,
  val documentSetReindexer: DocumentSetReindexer,
  val steps: Vector[Step],
  val progressReporter: ActorRef,
  val nDeciders: Int,                    // see Processor.scala docs
  val recurseBufferSize: Int,            // see Processor.scala docs
  val ingestBatchSize: Int,              // see Ingester.scala docs
  val ingestBatchMaxWait: FiniteDuration // see Ingester.scala docs
)(implicit mat: Materializer) {
  implicit val ec = mat.executionContext

  private val database = file2Writer.database
  private val blobStorage = file2Writer.blobStorage
  private val logger = Logger.forClass(getClass)

  private val fileGroupSource = new FileGroupSource(database, reportProgress _)

  private def reportProgress(progressState: FileGroupProgressState): Unit = {
    progressReporter ! progressState
  }
  private def noOpCancel(fileGroupId: Long): Unit = {}

  @volatile var cancelFileGroupJob: (Long => Unit) = noOpCancel _

  def enqueueFileGroup(
    fileGroup: FileGroup,
    onCompleteSendToActor: ActorRef,
    onCompleteMessage: DocumentSetMessageBroker.WorkerDoneDocumentSetCommand
  ): Unit = {
    fileGroupSource.enqueue.tell(
      FileGroupSource.JobAndOnCompleteMessage(fileGroup, onCompleteSendToActor, onCompleteMessage),
      null
    )
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
        AND file2_id IS NOT NULL
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
      _ <- reindexFuture
      _ <- AddDocumentsCommon.afterAddDocuments(documentSetId)
      _ <- database.delete(compiledFileGroup(fileGroup.id)) // lets user navigate to docset
    } yield {
      fileGroupJob.onCompleteSendToActor.tell(fileGroupJob.onCompleteMessage, null)

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

  /** For each ResumedFileGroupJob, emits an IngestedRootFile2 each time an ingest finishes.
    *
    * A ResumedFileGroupJob might not have any ingests remaining; if that's the case,
    * this graph will emit nothing.
    */
  def groupFile2Graph: Flow[ResumedFileGroupJob, IngestedRootFile2, Route] = {
    val groupedFileUploadToFile2 = new GroupedFileUploadToFile2(database, blobStorage)

    val processorFlowWithMat = new Processor(file2Writer, nDeciders, recurseBufferSize).flow(steps)

    Flow.fromGraph(GraphDSL.create(processorFlowWithMat) { implicit builder => processor =>
      import GraphDSL.Implicits._

      val resumer = builder.add(new FileGroupFile2Graph(database, groupedFileUploadToFile2).graph)
      val toIngester = builder.add(MergePreferred[ProcessedFile2](1))
      val ingester = builder.add(Ingester.ingest(file2Writer, ingestBatchSize, ingestBatchMaxWait))

      resumer.out0 ~> processor                           // WrittenFile2s
      resumer.out1 ~>              toIngester.preferred   // ProcessedFile2s: ingester should prefer these
                      processor ~> toIngester ~> ingester // other ProcessedFile2s

      new FlowShape(resumer.in, ingester.out)
    })
  }

  /** Emits each input ResumedFileGroupJob when there is nothing left to ingest.
    *
    * Materializes to a Route (for an HTTP server) and a cancel function (accepts a
    * fileGroupId).
    */
  def groupGraph: Flow[ResumedFileGroupJob, ResumedFileGroupJob, (Route, Long => Unit)] = {
    val inProgress = ArrayBuffer.empty[ResumedFileGroupJob]

    def cancelFileGroupJobByFileGroupId(fileGroupId: Long): Unit = synchronized {
      inProgress
        .filter(_.fileGroup.id == fileGroupId)
        .foreach(job => job.cancel)
    }
    def addInProgress(job: ResumedFileGroupJob) = synchronized { inProgress.+=(job); job }

    def shouldEmit(job: ResumedFileGroupJob) = synchronized {
      if (job.isComplete) {
        1
      } else {
        0
      }
    }

    val filterAndRemoveInProgress: PartialFunction[ResumedFileGroupJob, ResumedFileGroupJob] = synchronized {
      // Only pass-through jobs that are in progress and switching to completed.
      //
      // Behaves correctly with:
      //
      // * Canceled jobs (a canceled job will still complete)
      // * Completed jobs (once; then inProgress doesn't contain them)
      // * Unfinished jobs (they'll be swallowed)
      case job if (inProgress.contains(job) && shouldEmit(job) == 1) => {
        inProgress.-=(job)
        job
      }
    }

    Flow.fromGraph(GraphDSL.create(groupFile2Graph) { implicit builder => file2Flow =>
      // Emit a Job immediately if it's complete. Otherwise, emit the Job each
      // time any file is ingested.
      //
      // (Cancelling a job doesn't alter any logic. It just makes ingests faster.)
      import GraphDSL.Implicits._

      val input = builder.add(Flow[ResumedFileGroupJob].map(addInProgress _))
      val markFileIngested = builder.add(Flow[IngestedRootFile2].map(markFileIngestedInJob _))
      val splitEmptyJob = builder.add(Partition[ResumedFileGroupJob](2, shouldEmit))
      val merge = builder.add(Merge[ResumedFileGroupJob](2))


      input ~> splitEmptyJob
               splitEmptyJob.out(0) ~> file2Flow ~> markFileIngested ~> merge
               splitEmptyJob.out(1) ~> merge
      new FlowShape(input.in, merge.out)
    })
      .collect(filterAndRemoveInProgress)
      .mapMaterializedValue(route => (route, cancelFileGroupJobByFileGroupId))
  }

  private def startHttpServer(route: Route): Future[Http.ServerBinding] = {
    httpWorkerServer.bindAndHandle(route)
  }

  def run: Future[akka.Done] = {
    val (futureBinding: Future[Http.ServerBinding], futureDone: Future[akka.Done]) =
      fileGroupSource.source // ResumedFileGroupJob
        .viaMat(groupGraph)((_, tup) => {
          val route: Route = tup._1
          val cancelFileGroupJobByFileGroupId: Long => Unit = tup._2
          cancelFileGroupJob = cancelFileGroupJobByFileGroupId
          startHttpServer(route)
        })
        .named("FileGroupImportMonitor")
        .toMat(Sink.foreach(finishFileGroupJob _))(Keep.both)
        .run

    for {
      binding <- futureBinding
      _ <- futureDone
      _ <- binding.unbind
    } yield {
      cancelFileGroupJob = noOpCancel _
      akka.Done
    }
  }
}

object FileGroupImportMonitor {
  def withProgressReporter(
    actorSystem: ActorSystem,
    progressReporter: ActorRef
  ): FileGroupImportMonitor = {
    import com.typesafe.config.ConfigFactory
    val config = ConfigFactory.load
    val file2Writer = new File2Writer(
      Database(),
      BlobStorage,
      config.getInt("max_n_chars_per_document"),
    )
    implicit val materializer = Materializer.matFromSystem(actorSystem)

    val httpWorkerServer = HttpWorkerServer(
      actorSystem,
      config.getString("ingest.broker_http_address"),
      config.getInt("ingest.broker_http_port")
    )

    new FileGroupImportMonitor(
      file2Writer,
      httpWorkerServer,
      DocumentSetReindexer,
      Step.all(
        file2Writer,
        config.getInt("ingest.max_n_http_workers_per_step"),
        Duration.fromNanos(config.getDuration("ingest.worker_idle_timeout").toNanos),
        Duration.fromNanos(config.getDuration("ingest.worker_http_create_timeout").toNanos)
      ),
      progressReporter,
      config.getInt("ingest.n_document_identifiers"),
      config.getInt("ingest.max_recurse_buffer_length"),
      config.getInt("ingest.batch_size"),
      Duration.fromNanos(config.getDuration("ingest.batch_max_wait").toNanos)
    )
  }
}
