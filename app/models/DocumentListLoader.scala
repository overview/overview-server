/*
 * DocumentListLoader.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package models

import java.sql.Connection
import models.DatabaseStructure.DocumentData

/**
 * Abstract superclass for classes that need to load information about Documents based
 * on document ids.
 */
abstract class DocumentListLoader(loader: DocumentTagDataLoader, parser: DocumentListParser) {

  /**
   * @return Documents with the specified ids.
   */
  protected def loadDocumentList(documentIds: Seq[Long])(implicit c: Connection): Seq[core.Document] = {
    val documentData = loader.loadDocuments(documentIds)
    createWithDocumentData(documentData, documentIds)
  }

  /**
   * Helper method for the case where we don't want to repeat the query for documentData
   * but still need to load tag and node data.
   */
  protected def createWithDocumentData(documentData: Seq[DocumentData], documentIds: Seq[Long])(implicit c: Connection): Seq[core.Document] = {
    val documentTagData = loader.loadDocumentTags(documentIds)
    val documentNodeData = loader.loadNodes(documentIds)

    parser.createDocuments(documentData, documentTagData, documentNodeData)
  }
}
