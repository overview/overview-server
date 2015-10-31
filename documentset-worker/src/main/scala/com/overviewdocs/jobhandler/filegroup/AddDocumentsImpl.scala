package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.models.{File,GroupedFileUpload}

/** Turns GroupedFileUploads into Documents (and DocumentProcessingErrors).
  */
class AddDocumentsImpl(documentIdSupplier: ActorRef) {
  /** Processes one GroupedFileUpload.
    *
    * By the time this Future succeeds, one of several side effects may have
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
    */
  def processUpload(
    command: AddDocumentsFromFileGroup,
    upload: GroupedFileUpload
  )(implicit ec: ExecutionContext): Future[Unit] = {
    writeFile(upload, command.lang).flatMap(_ match {
      case Left(message) => writeDocumentProcessingError(command, upload, message)
      case Right(file) => {
        buildDocuments(file, command.splitDocuments).flatMap(_ match {
          case Left(message) => writeDocumentProcessingError(command, upload, message)
          case Right(documentsWithoutIds) => writeDocuments(command.documentSetId, documentsWithoutIds)
        })
      }
    })
  }

  private def detectDocumentType(
    upload: GroupedFileUpload
  )(implicit ec: ExecutionContext): Future[DocumentTypeDetector.DocumentType] = {
    Future(blocking {
      DocumentTypeDetector.detectForLargeObject(upload.name, upload.contentsOid)
    })
  }

  private def writeFile(
    upload: GroupedFileUpload,
    lang: String
  )(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    detectDocumentType(upload).flatMap(_ match {
      case DocumentTypeDetector.PdfDocument => task.CreatePdfFile(upload, lang)
      case DocumentTypeDetector.OfficeDocument => task.CreateOfficeFile(upload)
      case DocumentTypeDetector.UnsupportedDocument(mimeType) => Future.successful(Left(
        s"Overview doesn't support documents of type $mimeType"
      ))
    })
  }

  private def buildDocuments(
    file: File,
    splitByPage: Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[task.DocumentWithoutIds]]] = {
    splitByPage match {
      case true => task.CreateDocumentDataForPages(file)
      case false => task.CreateDocumentDataForFile(file)
    }
  }

  private def writeDocuments(
    documentSetId: Long,
    documentsWithoutIds: Seq[task.DocumentWithoutIds]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    task.WriteDocuments(documentSetId, documentsWithoutIds, documentIdSupplier)
  }

  private def writeDocumentProcessingError(
    command: AddDocumentsFromFileGroup,
    upload: GroupedFileUpload,
    message: String
  )(implicit ec: ExecutionContext): Future[Unit] = {
    task.WriteDocumentProcessingError(command.documentSetId, upload.name, message)
  }

  /** Deletes the Job from the database and freshens its DocumentSet info.
    *
    * In detail:
    *
    * 1. Ensures the DocumentSet has an alias in the search index.
    * 2. Updates the DocumentSet's document-ID array and counts.
    * 3. Deletes unprocessed GroupedFileUploads. (When the command is cancelled,
    *    these remain behind.)
    * 4. Deletes the DocumentSetCreationJob.
    * 5. Deletes the FileGroup.
    * 6. Creates a clustering DocumentSetCreationJob.
    */
  def finishJob(command: AddDocumentsFromFileGroup)(implicit ec: ExecutionContext): Future[Unit] = {
    import com.overviewdocs.database.FileGroupDeleter
    import com.overviewdocs.database.DocumentSetCreationJobDeleter
    import com.overviewdocs.searchindex.TransportIndexClient
    for {
      _ <- TransportIndexClient.singleton.addDocumentSet(command.documentSetId) // FIXME move this to creation
      _ <- task.DocumentSetInfoUpdater.update(command.documentSetId)
      _ <- FileGroupDeleter.delete(command.fileGroupId)
      _ <- DocumentSetCreationJobDeleter.delete(command.documentSetCreationJobId)
      // _ <- [create a DocumentSetCreationJob...]
    } yield {
      ()
    }
  }
}
