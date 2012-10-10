/*
 * DocumentLoader.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Jul 2012
 */
package models

import java.sql.Connection

/**
 * Loads a document from the database
 */
class DocumentLoader(loader: DocumentDataLoader = new DocumentDataLoader(),
                     parser: DocumentListParser = new DocumentListParser()) {

  
  def load(id: Long)(implicit c: Connection): Option[core.Document] = {
    val documentData = loader.loadDocument(id)
    val noTagsNeeded = Nil
    val noNodesNeeded = Nil
    
    parser.createDocuments(documentData.toList, noTagsNeeded, noNodesNeeded).headOption
  }
}
