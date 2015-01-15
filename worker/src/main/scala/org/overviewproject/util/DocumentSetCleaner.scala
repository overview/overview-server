package org.overviewproject.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.SlickClient
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.Documents

trait DocumentSetCleaner extends JobUpdater with SlickClient {
  
  def deleteDocuments(documentSetId: Long): Future[Unit] = db { implicit session =>
    val documents = Documents.filter(_.documentSetId === documentSetId) 
    
    documents.delete
  } 
}