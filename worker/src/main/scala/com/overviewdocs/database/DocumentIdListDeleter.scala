package com.overviewdocs.database

import scala.concurrent.Future

import com.overviewdocs.models.tables.DocumentIdLists

/** Deletes a DocumentIdList.
  */
trait DocumentIdListDeleter extends HasDatabase {
  import database.api._

  lazy val findByDocumentSet = Compiled { documentSetId: Rep[Int] =>
    DocumentIdLists
      .filter(_.documentSetId === documentSetId)
      .map(_.id)
  }

  def deleteByDocumentSet(documentSetId: Int): Future[Unit] = {
    database.delete(findByDocumentSet(documentSetId))
  }
}

object DocumentIdListDeleter extends DocumentIdListDeleter
