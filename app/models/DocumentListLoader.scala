package models

import java.sql.Connection
import models.DatabaseStructure.DocumentData

abstract class DocumentListLoader(loader: DocumentTagDataLoader, parser: DocumentListParser) {

  protected def loadDocumentList(documentIds: Seq[Long])(implicit c: Connection): Seq[core.Document] = {
    val documentData = loader.loadDocuments(documentIds)
    createWithDocumentData(documentData, documentIds)
  }

  protected def createWithDocumentData(documentData: Seq[DocumentData], documentIds: Seq[Long])(implicit c: Connection): Seq[core.Document] = {
    val documentTagData = loader.loadDocumentTags(documentIds)

    parser.createDocuments(documentData, documentTagData)
  }
}
