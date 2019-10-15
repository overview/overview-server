package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.tables.{Documents,DocumentSetFile2s,Files,File2s}

@ImplementedBy(classOf[DbDocumentSetFileBackend])
trait DocumentSetFileBackend extends Backend {
  /** Returns true if a file with the given sha1 exists in the given docset */
  def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]): Future[Boolean]
}

class DbDocumentSetFileBackend @Inject() (
  val database: Database
) extends DocumentSetFileBackend with DbBackend {
  import database.api._
  import database.executionContext

  lazy val byIdAndSha1 = Compiled { (documentSetId: Rep[Long], sha1: Rep[Array[Byte]]) =>
    // file1 -- deprecated
    for {
      file <- Files.filter(_.contentsSha1 === sha1)
      documents <- Documents.filter(_.fileId === file.id).filter(_.documentSetId === documentSetId)
    } yield (())
  }

  lazy val byIdAndSha1File2 = Compiled { (documentSetId: Rep[Long], sha1: Rep[Array[Byte]]) =>
    for {
      file2 <- File2s.filter(_.blobSha1 === sha1)
      documents <- DocumentSetFile2s.filter(_.file2Id === file2.id).filter(_.documentSetId === documentSetId)
    } yield (())
  }

  override def existsByIdAndSha1(documentSetId: Long, sha1: Array[Byte]) = {
    for {
      deprecated <- database.option(byIdAndSha1(documentSetId, sha1)).map(_.isDefined)
      current <- database.option(byIdAndSha1File2(documentSetId, sha1)).map(_.isDefined)
    } yield {
      deprecated || current
    }
  }
}
