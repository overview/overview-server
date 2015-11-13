package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef
import java.time.Instant
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.database.{HasDatabase,TreeIdGenerator}
import com.overviewdocs.models.{File,FileGroup,GroupedFileUpload,Tree}
import com.overviewdocs.models.tables.{FileGroups,GroupedFileUploads,Trees}
import com.overviewdocs.util.Logger

/** Turns GroupedFileUploads into Documents (and DocumentProcessingErrors).
  */
class AddDocumentsImpl(documentIdSupplier: ActorRef) {
  private val logger = Logger.forClass(getClass)

  /** Processes one GroupedFileUpload.
    *
    * By the time this Future succeeds, one of several side effects *may* have
    * occurred:
    *
    * * One File may have been written.
    * * One or more Pages may have been written.
    * * One or more Documents may have been written.
    * * One or more DocumentProcessingErrors may have been written.
    *
    * And one side-effect will certainly have occurred:
    *
    * * The GroupedFileUpload will be deleted, if it still exists.
    *
    * This method is resilient to every runtime error we expect. In particular:
    *
    * * If the DocumentSet gets deleted in a race, it will succeed.
    * * If the GroupedFileUpload gets deleted in a race, it will succeed.
    * * If the File gets deleted in a race, it will succeed.
    * * If the file cannot be parsed (e.g., it's an invalid PDF), it will write
    *   a DocumentProcessingError and succeed.
    *
    * If there's an error we *don't* expect (e.g., out of disk space), it will
    * return that error in the Future.
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

    writeFile(upload, fileGroup.lang.get, onProgress1).flatMap(_ match {
      case Left(message) => writeDocumentProcessingError(fileGroup.addToDocumentSetId.get, upload, message)
      case Right(file) => {
        onProgress(0.5)
        buildDocuments(file, fileGroup.splitDocuments.get, onProgress2).flatMap(_ match {
          case Left(message) => writeDocumentProcessingError(fileGroup.addToDocumentSetId.get, upload, message)
          case Right(documentsWithoutIds) => {
            if (onProgress(0.9)) {
              writeDocuments(fileGroup.addToDocumentSetId.get, documentsWithoutIds)
            } else {
              Future.successful(())
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
    lang: String,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    logger.debug("Creating File for {}", upload)
    detectDocumentType(upload).flatMap(_ match {
      case DocumentTypeDetector.PdfDocument => task.CreatePdfFile(upload, lang, onProgress)
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
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[task.DocumentWithoutIds]]] = {
    logger.debug("Reading documents from {}", file)
    splitByPage match {
      case true => task.CreateDocumentDataForPages(file, onProgress)
      case false => task.CreateDocumentDataForFile(file, onProgress)
    }
  }

  private def writeDocuments(
    documentSetId: Long,
    documentsWithoutIds: Seq[task.DocumentWithoutIds]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Writing {} documents into DocumentSet {}", documentsWithoutIds.length, documentSetId)
    task.WriteDocuments(documentSetId, documentsWithoutIds, documentIdSupplier)
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
    * 6. Creates a clustering DocumentSetCreationJob.
    */
  def finishJob(fileGroup: FileGroup)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Completing {}", fileGroup)
    import com.overviewdocs.background.filegroupcleanup.FileGroupRemover
    import com.overviewdocs.searchindex.TransportIndexClient
    for {
      _ <- TransportIndexClient.singleton.addDocumentSet(fileGroup.addToDocumentSetId.get) // FIXME move this to creation
      _ <- task.DocumentSetInfoUpdater.update(fileGroup.addToDocumentSetId.get)
      _ <- FileGroupRemover().remove(fileGroup.id)
      _ <- AddDocumentsImpl.createTree(fileGroup)
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

  def createTree(fileGroup: FileGroup)(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    for {
      treeId <- TreeIdGenerator.next(fileGroup.addToDocumentSetId.get)
      _ <- database.runUnit(Trees.+=(Tree.CreateAttributes(
        documentSetId=fileGroup.addToDocumentSetId.get,
        lang=fileGroup.lang.get
      ).toTreeWithId(treeId)))
    } yield ()
  }
}
