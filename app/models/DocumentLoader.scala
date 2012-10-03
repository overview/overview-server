package models

import java.sql.Connection

class DocumentLoader(loader: DocumentDataLoader = new DocumentDataLoader(),
                     parser: DocumentListParser = new DocumentListParser()) {

  def load(id: Long)(implicit c: Connection): Option[core.Document] = {
    val documentData = loader.loadDocument(id)
    val noTagsNeeded = Nil
    val noNodesNeeded = Nil
    
    parser.createDocuments(documentData.toList, noTagsNeeded, noNodesNeeded).headOption
  }
}
