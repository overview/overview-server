package org.overviewproject.jobhandler.filegroup.task

import java.io.{BufferedInputStream,InputStream}
import java.security.{DigestInputStream,MessageDigest}
import java.util.UUID
import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.Exception.ultimately

import org.overviewproject.blobstorage.{BlobStorage,BlobBucketId}
import org.overviewproject.database.{DB,SlickSessionProvider}
import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.models.{File,GroupedFileUpload}
import org.overviewproject.models.tables.Files
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.util.Logger

/**
 * Creates a [[File]] from a [[GroupedFileUpload]].
 * If necessary, the uploaded document is converted to PDF in order to provide a `File.view`.
 */
trait CreateFile {
  private val LargeObjectBufferSize = 5 * 1024 * 1024 // 5MB
  protected val blobStorage: BlobStorage

  private lazy val logger: Logger = Logger.forClass(getClass)
  private val PdfMagicNumber: Array[Byte] = "%PDF".getBytes
  private def await[A](f: => Future[A]): A = Await.result(f, Duration.Inf)

  /**
   * Creates a [[File]] from an [[GroupedFileUpload]]
   * If the first 4 bytes of the uploaded document correspond to "%PDF", the upload is used as the `File.view`.
   * Otherwise, the document is converted to PDF, if possible, and `File.view` is set to point to the PDF version
   * of the document. `File.contentsOid` refers to the original upload.
   *
   * @throws Exception on error. See [[DocumentConverter]] for details on conversion errors.
   */
  def apply(documentSetId: Long, upload: GroupedFileUpload): File = {
    val magicNumber = blocking(withLargeObjectInputStream(upload.contentsOid)(peekAtMagicNumber _))

    if (magicNumber.sameElements(PdfMagicNumber)) {
      applyPdf(documentSetId, upload)
    } else {
      applyNonPdf(documentSetId, upload)
    }
  }

  private def applyPdf(documentSetId: Long, upload: GroupedFileUpload): File = {
    var (location, sha1) = await(moveLargeObjectToBlobStorage(upload.contentsOid, upload.size))

    storage.createFile(documentSetId, upload.name, location, upload.size, sha1, location, upload.size)
  }

  private def applyNonPdf(documentSetId: Long, upload: GroupedFileUpload): File = {
    val contentFuture = moveLargeObjectToBlobStorage(upload.contentsOid, upload.size)
    val viewFuture = createViewInBlobStorage(upload)

    val future = for {
      (contentLocation, contentSha1) <- contentFuture
      (viewLocation, viewSize) <- viewFuture
    } yield storage.createFile(documentSetId, upload.name, contentLocation, upload.size, contentSha1, viewLocation, viewSize)

    await(future)
  }

  private def createViewInBlobStorage(upload: GroupedFileUpload): Future[(String, Long)] = {
    blocking(withLargeObjectInputStream(upload.contentsOid) { stream =>
      logger.logExecutionTime("Converting {} ({}, {}kb) to PDF", upload.name, upload.guid, upload.size / 1024) {
        converter.withStreamAsPdf(upload.guid, upload.name, stream) { (viewStream: InputStream, viewSize: Long) =>
          blobStorage.create(BlobBucketId.FileView, viewStream, viewSize)
            .map(location => (location, viewSize))
        }
      }
    })
  }

  private def peekAtMagicNumber(inputStream: InputStream): Array[Byte] = {
    // Let's not bother with the obvious optimization of reusing the stream.
    val magicNumber = new Array[Byte](PdfMagicNumber.length)
    inputStream.read(magicNumber, 0, magicNumber.length)
    magicNumber
  }

  private def moveLargeObjectToBlobStorage(oid: Long, size: Long): Future[(String,Array[Byte])] = {
    val lois = storage.getLargeObjectInputStream(oid)
    val digest = MessageDigest.getInstance("SHA-1")
    val dis = new DigestInputStream(lois, digest)
    val stream = new BufferedInputStream(dis, 5 * 1024 * 1024)

    blobStorage.create(BlobBucketId.FileContents, stream, size)
      .map((location) => (location, digest.digest))
      .andThen { case _ => stream.close }
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
  override protected val blobStorage = BlobStorage
  override protected val converter = MimeTypeDetectingDocumentConverter

  trait Storage {
    def getLargeObjectInputStream(oid: Long): InputStream
    def createFile(documentSetId: Long, name: String, contentsLocation: String, contentsSize: Long, contentsSha1: Array[Byte], viewLocation: String, viewSize: Long): File
  }

  object DatabaseStorage extends Storage {
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    override def getLargeObjectInputStream(oid: Long): InputStream = {
      new LargeObjectInputStream(oid, new SlickSessionProvider {})
    }

    private lazy val fileInserter = {
      import org.overviewproject.database.Slick.simple._
      Files
        .map(f => (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize))
        .returning(Files)
        .insertInvoker
    }

    override def createFile(
      documentSetId: Long,
      name: String,
      contentsLocation: String,
      contentsSize: Long,
      contentsSha1: Array[Byte],
      viewLocation: String,
      viewSize: Long)
    : File = {
      DB.withTransaction { connection =>
        val session = DB.slickSession(connection)
        val file = fileInserter.insert(1, name, contentsLocation, contentsSize, Some(contentsSha1), viewLocation, viewSize)(session)

        import org.squeryl.Session
        import org.overviewproject.postgres.{SquerylEntrypoint,SquerylPostgreSqlAdapter}

        SquerylEntrypoint.using(Session.create(connection, new SquerylPostgreSqlAdapter())) {
          tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))
        }
        file
      }
    }
  }
}
