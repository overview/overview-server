package controllers.backend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import org.overviewproject.models.tables.{Documents,Files}

trait DocumentSetFileBackend extends Backend {
  /** Returns true if a file with the given sha1 exists in the given docset */
  def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]): Future[Boolean]
}

trait DbDocumentSetFileBackend extends DocumentSetFileBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byIdAndSha1 = Compiled { (documentSetId: Column[Long], sha1: Column[Array[Byte]]) =>
    for {
      file <- Files.filter(_.contentsSha1 === sha1)
      documents <- Documents.filter(_.fileId === file.id).filter(_.documentSetId === documentSetId)
    } yield (())
  }

  override def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]) = {
    firstOption(byIdAndSha1(documentSetId, sha1))
      .map(_.isDefined)
  }
}

object DocumentSetFileBackend extends DbDocumentSetFileBackend with DbBackend
