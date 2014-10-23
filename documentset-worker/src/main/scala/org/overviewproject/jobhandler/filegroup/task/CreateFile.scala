package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream
import java.util.UUID

import scala.util.control.Exception._

import org.overviewproject.util.Logger
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
  private lazy val logger: Logger = Logger.forClass(getClass)

  protected val PdfMagicNumber: Array[Byte] = "%PDF".getBytes

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

      if (magicNumber.sameElements(PdfMagicNumber)) {
        storage.createFile(documentSetId, upload.name, upload.contentsOid, upload.size)
      } else {
        logger.logExecutionTime("Converting {} ({}, {}kb) to PDF", upload.name, upload.guid, upload.size / 1024) {
          converter.withStreamAsPdf(upload.guid, upload.name, stream)(storage.createFileWithPdfView(documentSetId, upload, _))
        }
      }
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

  protected val storage: CreateFile.Storage
  protected val converter: DocumentConverter
}

/** Implements [[CreateFile]] with database and conversion components */
object CreateFile extends CreateFile {
  override protected val storage = DatabaseStorage
  override protected val converter = MimeTypeDetectingDocumentConverter

  trait Storage {
    def getLargeObjectInputStream(oid: Long): InputStream
    def createFile(documentSetId: Long, name: String, oid: Long, size: Long): File
    def createFileWithPdfView(documentSetId: Long, upload: GroupedFileUpload, viewStream: InputStream): File
  }

  object DatabaseStorage extends Storage {
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    override def getLargeObjectInputStream(oid: Long): InputStream = new LargeObjectInputStream(oid)

    override def createFile(documentSetId: Long, name: String, oid: Long, size: Long): File = Database.inTransaction {
      val file = FileStore.insertOrUpdate(File(1, oid, oid, name, Some(size), Some(size)))
      tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))

      file
    }

    override def createFileWithPdfView(documentSetId: Long, upload: GroupedFileUpload, viewStream: InputStream): File = Database.inTransaction {
      implicit val pgc = DB.pgConnection(Database.currentConnection)
      
       
      val bufferSize = 8192
      val buffer = new Array[Byte](bufferSize)
      var offset = 0
      LO.withLargeObject { lo =>
        def copyStreamToLargeObject: Long = {
          val n = viewStream.read(buffer)
          if (n == -1) 0
          else {
            lo.add(buffer.take(n))
            n + copyStreamToLargeObject
          }
        }
        
        val size = copyStreamToLargeObject

        val file = FileStore.insertOrUpdate(File(1, upload.contentsOid, lo.oid, upload.name, Some(upload.size), Some(size)))
        tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))
        file
      }
    }
    
  }
}
