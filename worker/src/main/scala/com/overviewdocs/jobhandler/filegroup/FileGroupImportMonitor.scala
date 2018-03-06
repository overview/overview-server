package com.overviewdocs.jobhandler.filegroup

import akka.stream.{FlowShape,Graph,Materializer}
import akka.stream.scaladsl.{GraphDSL,MergePreferred,Source}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.database.Database
import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2,IngestedRootFile2}
import com.overviewdocs.ingest.pipeline.Step
import com.overviewdocs.ingest.{File2Writer,GroupedFileUploadToFile2,Processor,Ingester}
import com.overviewdocs.models.tables.{FileGroups,GroupedFileUploads}
import com.overviewdocs.util.{AddDocumentsCommon,Logger}

class FileGroupImportMonitor(
  val database: Database,
  val blobStorage: BlobStorage,
  val steps: Vector[Step],
  val maxNTextChars: Int,                // see File2Writer.scala docs
  val nDeciders: Int,                    // see Processor.scala docs
  val recurseBufferSize: Int,            // see Processor.scala docs
  val ingestBatchSize: Int,              // see Ingester.scala docs
  val ingestBatchMaxWait: FiniteDuration // see Ingester.scala docs
)(implicit ec: ExecutionContext, mat: Materializer) {
  private val logger = Logger.forClass(getClass)
  private val inProgress = ArrayBuffer.empty[ResumedFileGroupJob]

  private val fileGroupJobs: Source[ResumedFileGroupJob, akka.NotUsed] = {
    new FileGroupSource(database, reportProgress _).source
  }

  private def reportProgress(progressState: FileGroupProgressState): Unit = {
    //progressReporter ! progressState
  }

  private def finishIngesting(ingestedFile2: IngestedRootFile2): Future[Unit] = {
    val job = inProgress.find(_.fileGroup.addToDocumentSetId == Some(ingestedFile2.documentSetId))
    job match {
      case None => {
        logger.warn("Ingested File2 we were not expecting: {}", ingestedFile2.toString)
        Future.unit
      }
      case Some(job) => {
        job.progressState.incrementNFilesIngested
        if (job.isComplete) {
          finishJob(job)
        } else {
          Future.unit
        }
      }
    }
  }

  lazy val compiledGroupedFileUploadsForFileGroup = {
    import database.api._
    Compiled { fileGroupId: Rep[Long] =>
      GroupedFileUploads.filter(_.fileGroupId === fileGroupId)
    }
  }

  lazy val compiledFileGroup = {
    import database.api._
    Compiled { id: Rep[Long] =>
      FileGroups.filter(_.id === id)
    }
  }

  private def finishJob(job: ResumedFileGroupJob): Future[Unit] = {
    for {
      _ <- database.delete(compiledGroupedFileUploadsForFileGroup(job.fileGroup.id))
      _ <- database.delete(compiledFileGroup(job.fileGroup.id))
      // TODO _ <- reindexer.runJob(DocumentSetReindexJob(...)) -- maybe just reindex in AddDocumentsCommon?
      _ <- AddDocumentsCommon.afterAddDocuments(job.fileGroup.addToDocumentSetId.get)
    } yield ()
  }

  def cancelAllJobsForDocumentSet(documentSetId: Long): Unit = {
    inProgress
      .filter(_.documentSetId == documentSetId)
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
      resumer.out1 ~>              toIngester             // ProcessedFile2s: ingester should prefer these
                      processor ~> toIngester ~> ingester // other ProcessedFile2s

      new FlowShape(resumer.in, ingester.out)
    }
  }
}
