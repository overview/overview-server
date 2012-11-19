package csv

import overview.util.{DocumentConsumer, DocumentProducer}
import overview.util.Progress._

class CsvImportDocumentProducer(documentSetId: Long, uploadedFileId: Long, consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer {

  def produce() {}
}