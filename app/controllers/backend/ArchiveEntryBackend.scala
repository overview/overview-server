package controllers.backend

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import play.api.libs.iteratee.Enumerator
import org.postgresql.PGConnection
import scala.collection.mutable
import scala.concurrent.Future
import slick.dbio.{DBIOAction,Effect,NoStream,SynchronousDatabaseAction}
import slick.jdbc.JdbcBackend
import slick.util.DumpInfo

import com.overviewdocs.blobstorage.BlobStorage
import models.ArchiveEntry

trait ArchiveEntryBackend extends Backend {
  /** A list of ArchiveEntries, in arbitrary order. */
  def showMany(documentSetId: Long, documentIds: Seq[Long]): Future[Seq[ArchiveEntry]]

  /** File contents.
    *
    * The source of the contents depends upon the document:
    *
    * * If pageId is set, return the bytes at page.dataLocation
    * * Else, if fileId is set, return the bytes at file.viewLocation
    * * Else, return the document text encoded as UTF-8
    */
  def streamBytes(documentSetId: Long, documentId: Long): Enumerator[Array[Byte]]
}

trait DbArchiveEntryBackend extends ArchiveEntryBackend with DbBackend {
  protected val blobStorage: BlobStorage

  private class ShowManyAction(documentSetId: Long, documentIds: Seq[Long])
  extends SynchronousDatabaseAction[Seq[ArchiveEntry], NoStream, JdbcBackend, Effect.Read]
  {
    override def getDumpInfo = DumpInfo(s"DbDocumentFileInfoBackend.SelectAction(${documentSetId}, ...)")

    private val Pdf = ".pdf".getBytes("ascii")
    private val Txt = ".txt".getBytes("ascii")

    private def documentsSql: String = {
      val idsSql = {
        val sb = new StringBuilder("SELECT * FROM (VALUES (")
        sb.append(documentIds.head)
        sb.append(")")
        documentIds.tail.foreach { id =>
          sb.append(",(")
          sb.append(id)
          sb.append(")")
        }
        sb.append(") AS t(id)")
        sb.toString
      }

      s"""COPY (
        WITH selection AS (${idsSql})
        SELECT
          d.id,
          COALESCE(d.title, ''),
          d.file_id IS NOT NULL AS is_pdf,
          d.page_number,
          COALESCE(p.data_size, f.view_size, octet_length(d.text)) AS n_bytes
        FROM selection
        INNER JOIN document d ON selection.id = d.id
        LEFT JOIN file f ON d.file_id = f.id
        LEFT JOIN page p ON d.page_id = p.id
        WHERE d.document_set_id = $documentSetId
      ) TO STDOUT WITH BINARY"""
    }

    private def copyManager(context: JdbcBackend#Context) = {
      val pgConnection = context.connection.unwrap(classOf[PGConnection])
      pgConnection.getCopyAPI
    }

    override def run(context: JdbcBackend#Context): Seq[ArchiveEntry] = {
      val out = new ByteArrayOutputStream()
      copyManager(context).copyOut(documentsSql, out)
      val buf = ByteBuffer.wrap(out.toByteArray)

      val signature = new Array[Byte](11)
      buf.get(signature)
      assert(signature.sameElements("PGCOPY\n".getBytes("ascii") ++ Array(0xff, 0x0d, 0x0a, 0x00).map(_.toByte)))
      val flags = buf.getInt; assert(flags == 0)
      val headerExtensionLength = buf.getInt
      buf.position(buf.position + headerExtensionLength)

      val ret = new mutable.ArrayBuffer[ArchiveEntry](documentIds.length)

      while (buf.getShort != -1) {
        val idLength = buf.getInt
        assert(idLength == 8)
        val id: Long = buf.getLong

        val titleLength = buf.getInt
        val title: Array[Byte] = new Array[Byte](titleLength)
        buf.get(title)

        val isPdfLength = buf.getInt
        assert(isPdfLength == 1)
        val isPdf: Boolean = buf.get != 0

        val pageNumberLength = buf.getInt
        val maybePageNumber: Option[Int] = if (pageNumberLength == -1) None else Some(buf.getInt)

        val nBytesLength = buf.getInt
        assert(nBytesLength == 8)
        val nBytes = buf.getLong

        val titleBytes: Array[Byte] = (maybePageNumber, isPdf) match {
          case (Some(pageNumber), _) => {
            ArchiveEntry.sanitizeFilenameUtf8WithExtension(title, s"-p${pageNumber}.pdf".getBytes("ascii"))
          }
          case (_, true) => {
            ArchiveEntry.sanitizeFilenameUtf8WithExtension(title, Pdf)
          }
          case _ => {
            ArchiveEntry.sanitizeFilenameUtf8WithExtension(title, Txt)
          }
        }

        ret.append(ArchiveEntry(id, titleBytes, nBytes))
      }

      ret.toSeq
    }
  }

  override def showMany(documentSetId: Long, documentIds: Seq[Long]) = {
    database.run(new ShowManyAction(documentSetId, documentIds))
  }

  override def streamBytes(documentSetId: Long, documentId: Long) = {
    import database.api._
    import database.executionContext

    val q = sql"""
      SELECT
        document.file_id IS NOT NULL AS is_location,
        CASE
          WHEN document.page_id IS NOT NULL THEN (SELECT data_location FROM page WHERE id = document.page_id)
          WHEN document.file_id IS NOT NULL THEN (SELECT view_location FROM file WHERE id = document.file_id)
          ELSE COALESCE(document.text, '')
        END AS data_string
      FROM document
      WHERE document_set_id = $documentSetId AND id = $documentId
    """.as[(Boolean,String)]

    val future: Future[Enumerator[Array[Byte]]] = database.option(q).flatMap(_ match {
      case Some((true, location)) => blobStorage.get(location)
      case Some((false, text)) => Future.successful(Enumerator(text.getBytes("utf-8")))
      case None => Future.successful(Enumerator.empty)
    })

    Enumerator.flatten(future)
  }
}

object ArchiveEntryBackend extends DbArchiveEntryBackend {
  override val blobStorage = BlobStorage
}
