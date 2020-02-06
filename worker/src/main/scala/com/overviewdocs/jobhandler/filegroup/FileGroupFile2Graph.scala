package com.overviewdocs.jobhandler.filegroup

import akka.stream.{FanOutShape2,Graph,Materializer}
import akka.stream.scaladsl.{Flow,GraphDSL,Partition,Source}
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.Database
import com.overviewdocs.ingest.create.GroupedFileUploadToFile2
import com.overviewdocs.ingest.model.{ResumedFileGroupJob,BlobStorageRefWithSha1,WrittenFile2,ProcessedFile2,ProgressPiece}
import com.overviewdocs.models.{File2,FileGroup,GroupedFileUpload}
import com.overviewdocs.models.tables.{File2s,GroupedFileUploads}

/** Accepts FileGroups to process; outputs WrittenFile2s and ProcessedFile2s
  * that come from the database.
  *
  * This graph tackles the problem of resuming. When starting Overview, we want
  * to resume where we left off -- without re-processing what's already done.
  * Here's what's tricky:
  *
  * * Skip files completely if we've already ingested them.
  * * Skip processing if it's already done. To do this, we recurse into a
  *   ProcessedFile2's children, which can be WrittenFile2 or ProcessedFile2
  *   or IngestedFile2.
  * * Resume progress reporting, wiring up onProgress callbacks that behave
  *   the same as they did before the system went offline. Our concept is to
  *   visit the same files and wire up the same progress reporters, then mark
  *   processed files as 1.0.
  */
class FileGroupFile2Graph(
  val database: Database,
  val groupedFileUploadToFile2: GroupedFileUploadToFile2
) {
  private case class InternalFile2(writtenFile2: WrittenFile2, processedFile2Opt: Option[ProcessedFile2])
  private object InternalFile2 {
    def apply(file2: File2, fileGroupJob: ResumedFileGroupJob, progressPiece: ProgressPiece, nIngestedChildren: Int) = new InternalFile2(
      WrittenFile2(
        file2.id,
        fileGroupJob,
        progressPiece,
        file2.rootFile2Id,
        file2.parentFile2Id,
        file2.filename,
        file2.contentType,
        file2.languageCode,
        file2.metadata.jsObject,
        file2.wantOcr,
        file2.wantSplitByPage,
        BlobStorageRefWithSha1(file2.blob.get, file2.blobSha1)
      ),
      file2.processedAt.map(_ => ProcessedFile2(
        file2.id,
        fileGroupJob,
        file2.parentFile2Id,
        file2.nChildren.get,
        nIngestedChildren
      ))
    )
  }

  def graph(implicit ec: ExecutionContext, mat: Materializer): Graph[FanOutShape2[ResumedFileGroupJob, WrittenFile2, ProcessedFile2], akka.NotUsed] = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val load = builder.add(Flow.apply[ResumedFileGroupJob].flatMapConcat(loadInternalFile2s _))
      val partition = builder.add(Partition[InternalFile2](2, _.processedFile2Opt.size)) // None[ProcessedFile2] => 0; Some[ProcessedFile2] => 1
      val written = builder.add(Flow[InternalFile2].map(_.writtenFile2))
      val processed = builder.add(Flow[InternalFile2].collect { case InternalFile2(_, Some(processedFile2)) => processedFile2 })

      load ~> partition
              partition ~> written
              partition ~> processed

      new FanOutShape2(load.in, written.out, processed.out)
    }
  }

  private lazy val loadChildFile2s = {
    import database.api._
    Compiled { file2Id: Rep[Long] =>
      File2s
        .filter(_.parentFile2Id === file2Id)
        .filter(_.writtenAt.nonEmpty)
        // TODO don't load "text", because it's big. Ditto Metadata?
    }
  }

  private lazy val loadGroupedFileUploads = {
    import database.api._
    Compiled { fileGroupId: Rep[Long] =>
      GroupedFileUploads
        .filter(_.fileGroupId === fileGroupId)
    }
  }

  /** Loads File2s from the database, recursing, building onProgress from
    * `.progressState` and calling `.onProgress(1.0)` on Processed File2s.
    */
  private def loadInternalFile2s(
    job: ResumedFileGroupJob
  )(implicit ec: ExecutionContext): Source[InternalFile2, akka.NotUsed] = {
    Source.futureSource(database.seq(loadGroupedFileUploads(job.fileGroup.id)).map(Source.apply _))
      .flatMapConcat(gfu => loadFile2FromGroupedFileUpload(gfu, job))
      .mapMaterializedValue(_ => akka.NotUsed)
  }

  private def loadFile2FromGroupedFileUpload(
    groupedFileUpload: GroupedFileUpload,
    job: ResumedFileGroupJob
  )(implicit ec: ExecutionContext): Source[InternalFile2, akka.NotUsed] = {
    val retFuture = for {
      file2 <- groupedFileUploadToFile2.groupedFileUploadToFile2(job.fileGroup, groupedFileUpload)
      internalFiles <- internalizeRoot(file2, job)
    } yield {
      Source(internalFiles)
    }

    Source.futureSource(retFuture).mapMaterializedValue(_ => akka.NotUsed)
  }

  private def internalizeRoot(
    file2: File2,
    fileGroupJob: ResumedFileGroupJob
  )(implicit ec: ExecutionContext): Future[Vector[InternalFile2]] = {
    val file2Progress = fileGroupJob.progressState.buildFile2Progress(file2)
    internalizeOne(file2, fileGroupJob, file2Progress.progressPiece)
  }

  private def internalizeChildren(
    file2s: Vector[File2],
    fileGroupJob: ResumedFileGroupJob
  )(implicit ec: ExecutionContext): Future[Vector[InternalFile2]] = {
    // Icky code: will run lots of queries on resume
    val progressPiece = new ProgressPiece((_, _) => (), 0.0, 1.0) // TODO reconsider: think of the (unprocessed) children
    Future.sequence(file2s.map(file2 => internalizeOne(file2, fileGroupJob, progressPiece)))
      .map(vectors => vectors.flatMap(identity))
  }

  private def internalizeOne(
    file2: File2,
    fileGroupJob: ResumedFileGroupJob,
    progressPiece: ProgressPiece
  )(implicit ec: ExecutionContext): Future[Vector[InternalFile2]] = {
    if (file2.processedAt.nonEmpty && file2.nChildren.get > 0) {
      for {
        children <- database.seq(loadChildFile2s(file2.id))
        internalizedChildren <- internalizeChildren(children.filter(_.ingestedAt.isEmpty), fileGroupJob)
      } yield {
        val nIngestedChildren = children.filter(_.ingestedAt.nonEmpty).length
        Vector(InternalFile2(file2, fileGroupJob, progressPiece, nIngestedChildren)) ++ internalizedChildren
      }
    } else {
      Future.successful(Vector(InternalFile2(file2, fileGroupJob, progressPiece, 0)))
    }
  }
}
