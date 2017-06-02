package com.overviewdocs.database

import scala.concurrent.Future

import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.Documents

/** Finds a Document.
  */
trait DocumentFinder extends HasDatabase {
  import database.api._

  lazy val findDocumentCompiled = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
  }

  def findDocument(documentSetId: Long, documentId: Long): Future[Option[Document]] = {
    database.option(findDocumentCompiled(documentSetId, documentId))
  }
}

object DocumentFinder extends DocumentFinder
