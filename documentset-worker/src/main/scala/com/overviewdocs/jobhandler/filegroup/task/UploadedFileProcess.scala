package com.overviewdocs.jobhandler.filegroup.task

import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.jobhandler.filegroup.task.step._
import com.overviewdocs.jobhandler.filegroup.task.DocumentTypeDetector._
import com.overviewdocs.models.File
import com.overviewdocs.postgres.LargeObjectInputStream

class UploadedFileProcess(parameters: FilePipelineParameters)(implicit ec: ExecutionContext)
extends HasBlockingDatabase {
  def start: Future[Unit] = {
    writeFile.flatMap(_ match {
      case Left(documentProcessingError) => writeDocumentProcessingError(documentProcessingError)
      case Right(file) => {
        buildDocuments(file).flatMap(_ match {
          case Left(documentProcessingError) => writeDocumentProcessingError(documentProcessingError)
          case Right(documentsWithoutIds) => saveDocuments(documentsWithoutIds)
        })
      }
    })
  }

  private def detectDocumentType: Future[DocumentType] = {
    Future(blocking {
      DocumentTypeDetector.detect(
        parameters.filename,
        new LargeObjectInputStream(parameters.inputOid, blockingDatabase)
      )
    })
  }

  private def writeFile: Future[Either[String,File]] = {
    detectDocumentType.flatMap(_ match {
      case PdfDocument => CreatePdfFile(parameters)
      case OfficeDocument => CreateOfficeFile(parameters)
      case UnsupportedDocument(mimeType) => Future.successful(Left(
        s"Overview doesn't support documents of type $mimeType"
      ))
    })
  }

  private def buildDocuments(file: File): Future[Either[String,Seq[DocumentWithoutIds]]] = {
    parameters.splitDocuments match {
      case true => CreateDocumentDataForPages(file, parameters)
      case false => CreateDocumentDataForFile(file, parameters)
    }
  }

  private def saveDocuments(documentsWithoutIds: Seq[DocumentWithoutIds]): Future[Unit] = {
    WriteDocuments(documentsWithoutIds, parameters)
  }

  private def writeDocumentProcessingError(message: String): Future[Unit] = {
    WriteDocumentProcessingError(message, parameters)
  }
}
