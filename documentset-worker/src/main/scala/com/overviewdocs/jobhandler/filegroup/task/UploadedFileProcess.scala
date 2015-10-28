package com.overviewdocs.jobhandler.filegroup.task

import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.jobhandler.filegroup.task.step._
import com.overviewdocs.jobhandler.filegroup.task.DocumentTypeDetector._
import com.overviewdocs.models.File

class UploadedFileProcess(parameters: FilePipelineParameters) {
  def start(implicit ec: ExecutionContext): Future[Unit] = {
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

  private def detectDocumentType(implicit ec: ExecutionContext): Future[DocumentType] = {
    Future(blocking {
      DocumentTypeDetector.detectForLargeObject(parameters.filename, parameters.inputOid)
    })
  }

  private def writeFile(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    detectDocumentType.flatMap(_ match {
      case PdfDocument => CreatePdfFile(parameters)
      case OfficeDocument => CreateOfficeFile(parameters)
      case UnsupportedDocument(mimeType) => Future.successful(Left(
        s"Overview doesn't support documents of type $mimeType"
      ))
    })
  }

  private def buildDocuments(file: File)(implicit ec: ExecutionContext): Future[Either[String,Seq[DocumentWithoutIds]]] = {
    parameters.splitDocuments match {
      case true => CreateDocumentDataForPages(file, parameters)
      case false => CreateDocumentDataForFile(file, parameters)
    }
  }

  private def saveDocuments(documentsWithoutIds: Seq[DocumentWithoutIds])(implicit ec: ExecutionContext): Future[Unit] = {
    WriteDocuments(documentsWithoutIds, parameters)
  }

  private def writeDocumentProcessingError(message: String)(implicit ec: ExecutionContext): Future[Unit] = {
    WriteDocumentProcessingError(message, parameters)
  }
}
