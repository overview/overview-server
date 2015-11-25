package controllers.backend

import scala.concurrent.Future

import com.overviewdocs.models.CsvImport
import com.overviewdocs.models.tables.CsvImports

trait CsvImportBackend extends Backend {
  /** Saves and returns a new CsvImport.
    *
    * Be sure to notify the worker to begin working on this import; an abandoned
    * CsvImport makes no sense.
    */
  def create(attributes: CsvImport.CreateAttributes): Future[CsvImport]

  /** Marks a CsvImport as "cancelled", and returns true on success.
    *
    * A CsvImport may complete as usual, or the user may cancel it. Upon
    * cancellation, no more documents will be created from it.
    *
    * If the CsvImport does not exist or is complete, this is a no-op.
    */
  def cancel(documentSetId: Long, id: Long): Future[Boolean]
}

trait DbCsvImportBackend extends CsvImportBackend with DbBackend {
  import database.api._
  import database.executionContext

  private lazy val inserter = CsvImports.map(_.createAttributes).returning(CsvImports)

  private lazy val cancelCompiled = Compiled { (documentSetId: Rep[Long], csvImportId: Rep[Long]) =>
    CsvImports
      .filter(_.id === csvImportId)
      .filter(_.documentSetId === documentSetId)
      .filter(ci => ci.nBytesProcessed < ci.nBytes)
      .map(_.cancelled)
  }

  override def create(attributes: CsvImport.CreateAttributes): Future[CsvImport] = {
    database.run(inserter.+=(attributes))
  }

  override def cancel(documentSetId: Long, id: Long): Future[Boolean] = {
    database.run(cancelCompiled(documentSetId, id).update(true)).map(_ > 0)
  }
}

object CsvImportBackend extends DbCsvImportBackend
