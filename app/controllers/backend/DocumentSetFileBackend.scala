package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.tables.{Documents,Files}

trait DocumentSetFileBackend extends Backend {
  /** Returns true if a file with the given sha1 exists in the given docset */
  def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]): Future[Boolean]
}

trait DbDocumentSetFileBackend extends DocumentSetFileBackend with DbBackend {
  import databaseApi._

  lazy val byIdAndSha1 = Compiled { (documentSetId: Rep[Long], sha1: Rep[Array[Byte]]) =>
    for {
      file <- Files.filter(_.contentsSha1 === sha1)
      documents <- Documents.filter(_.fileId === file.id).filter(_.documentSetId === documentSetId)
    } yield (())
  }

  override def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]) = {
    database.option(byIdAndSha1(documentSetId, sha1))
      .map(_.isDefined)(database.executionContext)
  }
}

object DocumentSetFileBackend
  extends DbDocumentSetFileBackend
  with org.overviewproject.database.DatabaseProvider
