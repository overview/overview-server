package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.{Await,Future,blocking}
import org.overviewproject.blobstorage.{BlobBucketId,BlobStorage}
import org.overviewproject.database.{DB,Database}
import org.overviewproject.database.orm.Schema
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.database.orm.stores.GroupedFileUploadStore
import org.overviewproject.models.{GroupedFileUpload,Page}
import org.overviewproject.models.tables.{GroupedFileUploads,Pages}
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.DocumentProcessingError
import org.overviewproject.util.TempFile
import org.overviewproject.database.SlickSessionProvider
import scala.concurrent.duration.Duration

/** Provides database storage and pdfbox pdfProcessor implementations for [[CreatePagesProcess]] */
trait CreatePagesFromPdfWithStorage extends CreatePagesProcess {

  override protected val storage = DatabaseStorage()
  override protected val pdfProcessor = PdfBoxProcessor()
  override protected val createFile = CreateFile
  
  private object DatabaseStorage {
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    def apply(): Storage = new Storage with SlickSessionProvider {
      private lazy val pageInserter = {
        import org.overviewproject.database.Slick.simple._
        Pages
          .map { p => (p.fileId, p.pageNumber, p.dataLocation, p.dataSize, p.text) }
          .insertInvoker
      }

      def loadUploadedFile(uploadedFileId: Long): Option[GroupedFileUpload] = {
        import org.overviewproject.database.Slick.simple._

        DB.withConnection { connection =>
          val session = DB.slickSession(connection)
          GroupedFileUploads.filter(_.id === uploadedFileId).firstOption(session)
        }
      }

      def savePages(fileId: Long, pages: Iterable[Tuple2[Array[Byte],String]]): Unit = {
        import scala.concurrent.ExecutionContext.Implicits.global

        // The pages coming in are a view: only one is in memory at any given
        // moment. We'd like to upload them without forcing them into memory;
        // let's write them to temporary files.
        //
        // The tempfile stuff ought to be async, but it won't make a difference
        // -- the entire method is synchronous because that's the API.
        def upload(data: Array[Byte]): Future[String] = {
          val tempfile = new TempFile
          tempfile.outputStream.write(data)
          tempfile.outputStream.close
          BlobStorage.create(BlobBucketId.PageData, tempfile.inputStream, data.length)
          // yay, now data won't be in memory any more
        }

        def uploadAndBuildPageAttributes(pageNumber: Int, data: Array[Byte], text: String): Future[Page.CreateAttributes] = {
          val size = data.length // so data can be garbage-collected ASAP
          upload(data).map { (dataLocation: String) => Page.CreateAttributes(fileId, pageNumber, dataLocation, size, text) }
        }

        val pageAttributesFutures = pages
          .zipWithIndex
          .map({ (tuple: Tuple2[Array[Byte],String], index: Int) => uploadAndBuildPageAttributes(index + 1, tuple._1, tuple._2) }.tupled)

        val futurePageAttributes = Future.sequence(pageAttributesFutures)

        val pageAttributes = blocking {
          // SLOW: wait for all saves to finish
          Await.result(futurePageAttributes, Duration.Inf)
        }

        val tuples = pageAttributes
          .map { attrs =>
            (attrs.fileId, attrs.pageNumber, Some(attrs.dataLocation), attrs.dataSize, Some(attrs.text))
          }
          .toSeq

          
          val r = db { session =>
            session.withTransaction {
              pageInserter.insertAll(tuples: _*)(session)
            }
          }
          Await.result(r, Duration.Inf) // FIXME: make more async
      }

      def saveProcessingError(documentSetId: Long, uploadedFileId: Long, errorMessage: String): Unit = Database.inTransaction {
        val upload = GroupedFileUploadFinder.byId(uploadedFileId).headOption
        val filename = upload.map(_.name).getOrElse(s"Uploaded File Id: $uploadedFileId")
        
        val documentProcessingErrorStore = BaseStore(Schema.documentProcessingErrors)
        val error = DocumentProcessingError(documentSetId, filename, errorMessage)

        documentProcessingErrorStore.insertOrUpdate(error)
      }
    }
  }

  private object PdfBoxProcessor {
    def apply(): PdfProcessor = new PdfProcessor {
      override def loadFromBlobStorage(location: String): PdfDocument = new PdfBoxDocument(location)
    }
  }
}
