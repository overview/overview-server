package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.{ Documents, DocumentSets, DocumentSetUsers}

trait DocumentSetDeleter {

  protected def db[A](block: Session => A): Future[A]
  
  def delete(documentSetId: Long): Future[Unit] = db { implicit session => 
    val documents = Documents.filter(_.documentSetId === documentSetId) 
    val documentSetUser = DocumentSetUsers.filter(_.documentSetId === documentSetId)
    val documentSet = DocumentSets.filter(_.id === documentSetId) 

    documents.delete
    documentSetUser.delete
    documentSet.delete
   
  }
}