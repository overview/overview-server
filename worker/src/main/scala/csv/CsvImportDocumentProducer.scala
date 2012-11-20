package csv

import overview.util.{ DocumentConsumer, DocumentProducer }
import overview.util.Progress._
import database.DB

class CsvImportDocumentProducer(documentSetId: Long, uploadedFileId: Long, consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer {

  private val uploadReader = new UploadReader(uploadedFileId)

  def produce() {
    DB.withConnection { implicit connection =>
      uploadReader.read { reader =>
        val documentSource = new CsvImportSource(reader)
        
        documentSource.foreach { doc =>
          val documentId = doc.write(documentSetId)
          consumer.processDocument(documentId, doc.text)
        }
        
        consumer.productionComplete()
      }
    }
  }
}