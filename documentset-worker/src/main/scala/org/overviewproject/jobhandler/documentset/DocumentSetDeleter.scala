package org.overviewproject.jobhandler.documentset

/**
 * Methods for deleting all the data associated with document sets in the database
 * Client code needs to manage the proper order of calls.
 */
trait DocumentSetDeleter {
  def deleteClientGeneratedInformation(documentSetId: Long): Unit
  def deleteSearchGeneratedInformation(documentSetId: Long): Unit
  def deleteClusteringGeneratedInformation(documentSetId: Long): Unit
  def deleteUserRelatedInformation(documentSetId: Long): Unit
  def deleteDocuments(documentSetId: Long): Unit
  def deleteDocumentSet(documentSetId: Long): Unit

}

object DocumentSetDeleter {
  import org.overviewproject.postgres.SquerylEntrypoint._
  import org.overviewproject.database.Database
  import org.overviewproject.database.orm.finders._
  import org.overviewproject.database.orm.stores._

  def apply() = new DocumentSetDeleter {
    def deleteClientGeneratedInformation(documentSetId: Long): Unit = Database.inTransaction {
      LogEntryStore.delete(LogEntryFinder.byDocumentSet(documentSetId).toQuery)
    }

    def deleteSearchGeneratedInformation(documentSetId: Long): Unit = ???
    def deleteClusteringGeneratedInformation(documentSetId: Long): Unit = ???
    def deleteUserRelatedInformation(documentSetId: Long): Unit = ???
    def deleteDocuments(documentSetId: Long): Unit = ???
    def deleteDocumentSet(documentSetId: Long): Unit = ???
  }
}