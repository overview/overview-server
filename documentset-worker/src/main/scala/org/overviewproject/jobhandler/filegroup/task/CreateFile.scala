package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream
import java.util.UUID

import scala.util.control.Exception._

import org.overviewproject.database.DB
import org.overviewproject.database.Database
import org.overviewproject.database.orm.Schema
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.postgres.LO
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.tree.orm.stores.BaseStore

/**
 * Creates a [[File]] from a [[GroupedFileUpload]].
 * If necessary, the uploaded document is converted to PDF in order to provide a `File.view`.
 */
trait CreateFile {
  val PdfMagicNumber: Array[Byte] = "%PDF".getBytes

  /**
   * Creates a [[File]] from an [[GroupedFileUpload]]
   * If the first 4 bytes of the uploaded document correspond to "%PDF", the upload is used as the `File.view`.
   * Otherwise, the document is converted to PDF, if possible, and `File.view` is set to point to the PDF version
   * of the document. `File.contentsOid` refers to the original upload.
   *
   * @throws Exception on error. See [[DocumentConverter]] for details on conversion errors.
   */
  def apply(documentSetId: Long, upload: GroupedFileUpload): File =
    withLargeObjectInputStream(upload.contentsOid) { stream =>
      val magicNumber = peekAtMagicNumber(stream)

      if (magicNumber.sameElements(PdfMagicNumber)) storage.createFile(documentSetId, upload.name, upload.contentsOid)
      else converter.convertStreamToPdf(upload.guid, stream)(storage.createFileWithPdfView(documentSetId, upload, _))
    }

  private def peekAtMagicNumber(inputStream: InputStream): Array[Byte] = {
    val magicNumberBytes = 4
    val allowedReadAhead = magicNumberBytes * 2
    val magicNumber = new Array[Byte](4)

    inputStream.mark(allowedReadAhead)
    inputStream.read(magicNumber, 0, magicNumberBytes)
    inputStream.reset

    magicNumber
  }

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = storage.getLargeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }

  protected val storage: Storage
  protected val converter: DocumentConverter

  protected trait Storage {
    def getLargeObjectInputStream(oid: Long): InputStream
    def createFile(documentSetId: Long, name: String, oid: Long): File
    def createFileWithPdfView(documentSetId: Long, upload: GroupedFileUpload, viewStream: InputStream): File
  }

  protected trait DocumentConverter {
    def convertStreamToPdf[T](guid: UUID, documentStream: InputStream)(f: InputStream => T): T

  }
}

/** Implements [[CreateFile]] with database and conversion components */
object CreateFile extends CreateFile {
  override protected val storage: Storage = new DatabaseStorage
  override protected val converter: DocumentConverter = new LibreOfficeDocumentConverter

  class DatabaseStorage extends Storage {
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    override def getLargeObjectInputStream(oid: Long): InputStream = new LargeObjectInputStream(oid)

    override def createFile(documentSetId: Long, name: String, oid: Long): File = Database.inTransaction {
      val file = FileStore.insertOrUpdate(File(1, oid, oid, name))
      tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))

      file
    }

    override def createFileWithPdfView(documentSetId: Long, upload: GroupedFileUpload, viewStream: InputStream): File = Database.inTransaction {
      implicit val pgc = DB.pgConnection(Database.currentConnection)

      val buffer = new Array[Byte](8192)
      var offset = 0
      LO.withLargeObject { lo =>
        while (viewStream.read(buffer, 0, 8192) != -1) {
          lo.add(buffer)
        }

        val file = FileStore.insertOrUpdate(File(1, upload.contentsOid, lo.oid, upload.name))
        tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))
        file
      }
    }
  }

  class LibreOfficeDocumentConverter extends DocumentConverter {
    override def convertStreamToPdf[T](guid: UUID, documentStream: InputStream)(f: InputStream => T): T =
      DocumentConverter.convertToPdfStream(guid, documentStream)(f)
  }
}