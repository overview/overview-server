package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.models.tables.{Documents,Files}

@ImplementedBy(classOf[DbDocumentSetFileBackend])
trait DocumentSetFileBackend extends Backend {
  /** Returns true if a file with the given sha1 exists in the given docset */
  def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]): Future[Boolean]
}

class DbDocumentSetFileBackend @Inject() extends DocumentSetFileBackend with DbBackend {
  import database.api._
  import database.executionContext

  lazy val byIdAndSha1 = Compiled { (documentSetId: Rep[Long], sha1: Rep[Array[Byte]]) =>
    for {
      file <- Files.filter(_.contentsSha1 === sha1)
      documents <- Documents.filter(_.fileId === file.id).filter(_.documentSetId === documentSetId)
    } yield (())
  }

  override def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]) = {
    database.option(byIdAndSha1(documentSetId, sha1)).map(_.isDefined)
  }
}
