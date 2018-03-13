package com.overviewdocs.jobhandler.filegroup

import akka.actor.{ActorRef,ActorSystem}
import akka.http.scaladsl.{Http,HttpExt}
import akka.http.scaladsl.server.{Route,RoutingLog}
import akka.http.scaladsl.settings.{ParserSettings,RoutingSettings}
import akka.stream.{FlowShape,Graph,Materializer}
import akka.stream.scaladsl.{Flow,GraphDSL,Keep,MergePreferred,Source,Sink}
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
  val file2Writer: File2Writer,
  val httpConfig: FileGroupImportMonitor.HttpConfig,
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

  def graph: Flow[ResumedFileGroupJob, IngestedRootFile2, Route] = {
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

  private def addInProgress(job: ResumedFileGroupJob): Unit = synchronized { inProgress.+=(job) }
  private def removeInProgress(job: ResumedFileGroupJob): Unit = synchronized { inProgress.-=(job) }
  private def isInProgress(job: ResumedFileGroupJob): Boolean = synchronized { inProgress.contains(job) }

  private def startHttpServer(route: Route): Future[Http.ServerBinding] = {
    httpConfig.bindAndHandle(route)
  }

  def run: Future[akka.Done] = {
    val (futureBinding: Future[Http.ServerBinding], futureDone: Future[akka.Done]) =
      fileGroupSource.source // ResumedFileGroupJob
        .map { job => addInProgress(job); job }
        .viaMat(graph)((_, route) => startHttpServer(route)) // IngestedFile2Root
        .map(markFileIngestedInJob _)                        // ResumedFileGroupJob
        .filter(_.isComplete)                                // Complete ResumedFileGroupJob
        .filter(isInProgress _)                              // avoid a warning
        .map { job => removeInProgress(job); job }
        .mapAsync(1)(finishFileGroupJob _)                   // Unit -- now the FileGroup is deleted
        .toMat(Sink.ignore)(Keep.both)
        .run

    for {
      binding <- futureBinding
      _ <- futureDone
      _ <- binding.unbind
    } yield akka.Done
  }
}

object FileGroupImportMonitor {
  case class HttpConfig(
    httpExt: HttpExt,
    routingSettings: RoutingSettings,
    parserSettings: ParserSettings,
    routingLog: RoutingLog,
    interface: String,
    port: Int
  ) {
    def bindAndHandle(
      route: Route
    )(implicit mat: Materializer): Future[Http.ServerBinding] = {
      implicit val rs = routingSettings
      implicit val ps = parserSettings
      implicit val rl = routingLog
      val sealedRoute = Route.seal(route)
      val handler = Route.asyncHandler(sealedRoute)
      httpExt.bindAndHandleAsync(handler, interface, port)
    }
  }

  def withProgressReporter(
    actorSystem: ActorSystem,
    progressReporter: ActorRef
  )(implicit mat: Materializer): FileGroupImportMonitor = {
    import com.typesafe.config.ConfigFactory
    val config = ConfigFactory.load
    val file2Writer = new File2Writer(
      Database(),
      BlobStorage,
      config.getInt("max_n_chars_per_document"),
    )

    val routingSettings = RoutingSettings(actorSystem)
    val parserSettings = ParserSettings(actorSystem)
    val routingLog = RoutingLog.fromActorSystem(actorSystem)

    val httpConfig = HttpConfig(
      Http(actorSystem),
      routingSettings,
      parserSettings,
      routingLog,
      config.getString("ingest.broker_http_address"),
      config.getInt("ingest.broker_http_port")
    )

    new FileGroupImportMonitor(
      file2Writer,
      httpConfig,
      DocumentSetReindexer,
      com.overviewdocs.ingest.pipeline.Step.all(
        file2Writer,
        config.getInt("ingest.n_document_converters"),
        Duration.fromNanos(config.getDuration("ingest.worker_idle_timeout").toNanos),
        Duration.fromNanos(config.getDuration("ingest.worker_http_create_timeout").toNanos)
      ),
      progressReporter,
      config.getInt("ingest.n_document_converters"),
      config.getInt("ingest.max_recurse_buffer_length"),
      config.getInt("ingest.batch_size"),
      Duration.fromNanos(config.getDuration("ingest.batch_max_wait").toNanos)
    )
  }
}
