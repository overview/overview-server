package com.overviewdocs.jobhandler.filegroup

import akka.actor.{ActorRef,ActorRefFactory}
import java.time.Instant
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{File,FileGroup,GroupedFileUpload}
import com.overviewdocs.models.tables.{FileGroups,GroupedFileUploads}
import com.overviewdocs.util.{AddDocumentsCommon,Logger}

/** Turns GroupedFileUploads into Documents (and DocumentProcessingErrors).
  */
class AddDocumentsImpl(documentIdSupplier: ActorRef)(implicit system: ActorRefFactory) {
  private val logger = Logger.forClass(getClass)

  /** Processes one GroupedFileUpload.
    *
    * By the time this Future succeeds, several side effects will have occured:
    *
    * * A _root_ File2 will have been written.
    * * Derived _leaf_ File2s will have been written.
    * * Documents and DocumentProcessingErrors will have been written.
    * * The GroupedFileUpload will be deleted, if it still exists.
    *
    * These side effects are contingent on the DocumentSet and GroupedFileUpload
    * _not_ being deleted during its run. That makes this method resilient to
    * every runtime error we expect. In particular:
    *
    * * If the DocumentSet gets deleted in a race, it will succeed.
    * * If the GroupedFileUpload gets deleted in a race, it will succeed.
    * * If a File2 gets deleted in a race, it will succeed.
    * * For each input file we can't process (e.g., an invalid PDF), it will
    *   write a DocumentProcessingError and succeed.
    *
    * If there's an error we *don't* expect (e.g., out of disk space), it will
    * return that error in the Future. Callers are encouraged to crash at that
    * point.
    *
    * All writes maintain a record of how they were generated. To resume an
    * incomplete processUpload() when the worker restarts, we _could_ simply
    * delete all the derived data and re-generate it. Instead, we take a more
    * fine-grained approach.
    *
    * @param fileGroup FileGroup that contains the upload.
    * @param upload GroupedFileUpload that needs to be processed.
    * @param onProgress Function that reports progress. Parameter is between
    *                   `0.0` and `1.0`, inclusive. If it returns False, that
    *                   means we want to cancel.
    */
  def processUpload(
    fileGroup: FileGroup,
    upload: GroupedFileUpload,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Unit] = {
    def onProgress1(progress1: Double): Boolean = onProgress(progress1 * 0.5)
    def onProgress2(progress2: Double): Boolean = onProgress(0.5 + progress2 * 0.4)

    writeFile(upload, fileGroup.ocr.get, fileGroup.lang.get, onProgress1).flatMap(_ match {
      case Left(message) => writeDocumentProcessingError(fileGroup.addToDocumentSetId.get, upload, message)
      case Right(file) => {
        onProgress(0.5)
        buildDocuments(file, fileGroup.splitDocuments.get, onProgress2).flatMap(_ match {
          case Left(message) => writeDocumentProcessingError(fileGroup.addToDocumentSetId.get, upload, message)
          case Right(documentsWithoutIds) => {
            if (onProgress(0.9)) {
              writeDocuments(
                fileGroup.addToDocumentSetId.get,
                documentsWithoutIds,
                upload.documentMetadataJson.getOrElse(fileGroup.metadataJson)
              )
            } else {
              Future.unit
            }
          }
        })
      }
    })
      .flatMap(_ => deleteUpload(upload))
  }

  private def detectDocumentType(
    upload: GroupedFileUpload
  )(implicit ec: ExecutionContext): Future[DocumentTypeDetector.DocumentType] = {
    logger.debug("Detecting document type for {}", upload)
    Future(blocking {
      DocumentTypeDetector.detectForLargeObject(upload.name, upload.contentsOid)
    })
  }

  private def writeFile(
    upload: GroupedFileUpload,
    ocr: Boolean,
    lang: String,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    logger.debug("Creating File for {}", upload)
    detectDocumentType(upload).flatMap(_ match {
      case DocumentTypeDetector.PdfDocument => task.CreatePdfFile(upload, ocr, lang, onProgress)
      case DocumentTypeDetector.OfficeDocument => task.CreateOfficeFile(upload)
      case DocumentTypeDetector.UnsupportedDocument(mimeType) => Future.successful(Left(
        s"Overview doesn't support documents of type $mimeType"
      ))
    })
  }

  private def buildDocuments(
    file: File,
    splitByPage: Boolean,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[task.IncompleteDocument]]] = {
    Future.successful(Left("pipeline disabled"))
//    logger.debug("Reading documents from {}", file)// thumbnail for first page
//    splitByPage match {
//      case true => task.CreateDocumentDataForPages(file, onProgress)
//      case false => task.CreateDocumentDataForFile(file, onProgress)
//    }
  }

  private def writeDocuments(
    documentSetId: Long,
    documentsWithoutIds: Seq[task.IncompleteDocument],
    metadataJson: JsObject
  )(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Writing {} documents into DocumentSet {}", documentsWithoutIds.length, documentSetId)
    task.WriteDocuments(documentSetId, documentsWithoutIds, metadataJson, documentIdSupplier)
  }

  private def writeDocumentProcessingError(
    documentSetId: Long,
    upload: GroupedFileUpload,
    message: String
  )(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Writing DocumentProcessingError {} for {} on DocumentSet {}", message, upload, documentSetId)
    task.WriteDocumentProcessingError(documentSetId, upload.name, message)
  }

  private def deleteUpload(upload: GroupedFileUpload)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Deleting {}", upload)
    AddDocumentsImpl.deleteUpload(upload)
  }

  /** Deletes the Job from the database and freshens its DocumentSet info.
    *
    * In detail:
    *
    * 1. Ensures the DocumentSet has an alias in the search index.
    * 2. Updates the DocumentSet's document-ID array and counts.
    * 3. Deletes unprocessed GroupedFileUploads. (When the command is cancelled,
    *    GroupedFileUploads that haven't been visited will still be there.)
    * 5. Deletes the FileGroup.
    */
  def finishJob(fileGroup: FileGroup)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Completing {}", fileGroup)
    import com.overviewdocs.background.filegroupcleanup.FileGroupRemover
    for {
      _ <- AddDocumentsCommon.afterAddDocuments(fileGroup.addToDocumentSetId.get)
      _ <- FileGroupRemover().remove(fileGroup.id)
    } yield {
      ()
    }
  }

  /** Updates progress in the database.
    *
    * If the file group does not exist, this is a no-op.
    */
  def writeProgress(
    fileGroupId: Long,
    nFilesProcessed: Int,
    nBytesProcessed: Long,
    estimatedCompletionTime: Instant
  )(implicit ec: ExecutionContext): Future[Unit] = {
    AddDocumentsImpl.writeProgress(fileGroupId, nFilesProcessed, nBytesProcessed, estimatedCompletionTime)
  }
}

object AddDocumentsImpl extends HasDatabase {
  private lazy val writeProgressCompiled = {
    import com.overviewdocs.database.Slick.api._

    Compiled { fileGroupId: Rep[Long] =>
      FileGroups
        .filter(_.id === fileGroupId)
        .map(g => (g.nFilesProcessed, g.nBytesProcessed, g.estimatedCompletionTime))
    }
  }

  private lazy val compiledGroupedFileUpload = {
    import com.overviewdocs.database.Slick.api._

    Compiled { uploadId: Rep[Long] =>
      GroupedFileUploads.filter(_.id === uploadId)
    }
  }

  def writeProgress(
    fileGroupId: Long,
    nFilesProcessed: Int,
    nBytesProcessed: Long,
    estimatedCompletionTime: Instant
  )(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    database.runUnit(writeProgressCompiled(fileGroupId).update((
      Some(nFilesProcessed),
      Some(nBytesProcessed),
      Some(estimatedCompletionTime)
    )))
  }

  def deleteUpload(upload: GroupedFileUpload)(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    val action = for {
      _ <- database.largeObjectManager.unlink(upload.contentsOid)
      _ <- compiledGroupedFileUpload(upload.id).delete
    } yield ()
    database.run(action.transactionally)
  }
}
