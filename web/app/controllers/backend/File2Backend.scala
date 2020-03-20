package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.models.tables.File2s

@ImplementedBy(classOf[DbFile2Backend])
trait File2Backend extends Backend {
  /** Returns a BlobStorageRef to the given File2's Blob. */
  def lookupBlob(file2Id: Long): Future[Option[BlobStorageRef]]

  /** Returns a File2. */
  def lookup(file2Id: Long): Future[Option[File2]]

  /** Returns a BlobStorageRef to the given File2's Thumbnail. */
  def lookupThumbnailBlobAndContentType(file2Id: Long): Future[Option[(BlobStorageRef,String)]]

  /** Return a lookup map from leaf-file2 ID to root-file2. */
  def indexRoots(leafFile2Ids: Vector[Long]): Future[Map[Long,File2]]
}

class DbFile2Backend @Inject() (val database: Database) extends File2Backend with DbBackend {
  import database.api._
  import database.executionContext

  private lazy val lookupCompiled = Compiled { file2Id: Rep[Long] =>
    File2s.filter(_.id === file2Id)
  }

  private lazy val lookupBlobCompiled = Compiled { file2Id: Rep[Long] =>
    File2s
      .filter(_.id === file2Id)
      .map(f2 => (f2.blobLocation, f2.blobNBytes))
  }

  private lazy val lookupThumbnailBlobCompiled = Compiled { file2Id: Rep[Long] =>
    File2s
      .filter(_.id === file2Id)
      .map(f2 => (f2.thumbnailBlobLocation, f2.thumbnailBlobNBytes, f2.thumbnailContentType.getOrElse("image/png")))
  }

  private def blobify(tuple: Option[Tuple2[Option[String], Option[Long]]]): Option[BlobStorageRef] = {
    tuple match {
      case Some((Some(location), Some(nBytes))) => Some(BlobStorageRef(location, nBytes))
      case _ => None
    }
  }

  override def lookup(file2Id: Long) = {
    database.option(lookupCompiled(file2Id))
  }

  override def lookupBlob(file2Id: Long) = {
    database.option(lookupBlobCompiled(file2Id)).map(blobify _)
  }

  override def lookupThumbnailBlobAndContentType(file2Id: Long) = {
    database.option(lookupThumbnailBlobCompiled(file2Id)).map(_ match {
      case Some((Some(location), Some(nBytes), contentType)) => {
        Some((BlobStorageRef(location, nBytes), contentType))
      }
      case _ => None
    })
  }

  override def indexRoots(file2Ids: Vector[Long]) = {
    val leafsWithRoots = File2s.filter(_.id inSet file2Ids).join(File2s).on((leaf, root) => leaf.rootFile2Id === root.id)
    database.seq(leafsWithRoots.map(tup => (tup._1.id, tup._2))).map(_.toMap)
  }
}
