package csv

import overview.util.{ DocumentConsumer, DocumentProducer }
import overview.util.Progress._
import database.DB
import overview.util.DocumentSetCreationJobStateDescription._

class CsvImportDocumentProducer(documentSetId: Long, uploadedFileId: Long, consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer {

  private val FetchingFraction = 0.9
  private val uploadReader = new UploadReader(uploadedFileId)

  def produce() {
    DB.withTransaction { implicit connection =>
      uploadReader.read { reader =>
        val documentSource = new CsvImportSource(reader)

        documentSource.foreach { doc =>
          val documentId = writeAndCommitDocument(documentSetId, doc)
          consumer.processDocument(documentId, doc.text)
          progAbort(Progress(FetchingFraction * uploadReader.bytesRead / uploadReader.size.get, Retrieving(uploadReader.bytesRead.toInt, uploadReader.size.get.toInt)))
        }

        consumer.productionComplete()
      }
    }
  }

  private def writeAndCommitDocument(documentSetId: Long, doc: CsvImportDocument): Long =
    DB.withConnection { implicit connection =>
      doc.write(documentSetId)
    }
}