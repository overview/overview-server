/*
 * CsvImportDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package csv

import overview.database.DB
import overview.util.{ DocumentConsumer, DocumentProducer }
import overview.util.DocumentSetCreationJobStateDescription._
import overview.util.Progress._

/**
 * Feed the consumer documents generated from the uploaded file specified by uploadedFileId
 */
class CsvImportDocumentProducer(documentSetId: Long, uploadedFileId: Long, consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer {

  private val FetchingFraction = 0.9
  private val uploadReader = new UploadReader(uploadedFileId)
  private var bytesRead = 0l
  
  /** Start parsing the CSV upload and feeding the result to the consumer */
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
    // The input stream is buffered so new documents may be produced while
    // bytesRead stays the same. Only update if there is a change.
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