package models

import java.sql.Connection

class DocumentLoader(loader: DocumentDataLoader = new DocumentDataLoader(),
					 parser: DocumentDataParser = new DocumentDataParser()) {

  def load(id: Long)(implicit c: Connection): Option[core.Document] = {
    val documentData = loader.loadDocument(id)
    
    parser.createDocument(documentData)
  }
}