package csv

import overview.util.{ DocumentConsumer, DocumentProducer }
import overview.util.Progress._
import overview.database.DB
import overview.util.DocumentSetCreationJobStateDescription._

class CsvImportDocumentProducer(documentSetId: Long, uploadedFileId: Long, consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer {

  private val FetchingFraction = 0.9
  private val uploadReader = new UploadReader(uploadedFileId)
  private var bytesRead = 0l
  
  def produce() {
    DB.withTransaction { implicit connection =>
      uploadReader.read { reader =>
        val documentSource = new CsvImportSource(reader)

        documentSource.foreach { doc =>
          val documentId = writeAndCommitDocument(documentSetId, doc)
          consumer.processDocument(documentId, doc.text)
          reportProgress(uploadReader.bytesRead, uploadReader.size.getOrElse(1))
        }

        consumer.productionComplete()
      }
    }
  }
  
  private def reportProgress(n: Long, size: Long) {
    if (n != bytesRead) {
      bytesRead = n
      progAbort(Progress(FetchingFraction * bytesRead / size, Parsing(bytesRead, size)))
    }
  }

  private def writeAndCommitDocument(documentSetId: Long, doc: CsvImportDocument): Long =
    DB.withConnection { implicit connection =>
      doc.write(documentSetId)
    }
}