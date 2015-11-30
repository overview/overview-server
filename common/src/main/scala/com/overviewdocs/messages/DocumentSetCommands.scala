package com.overviewdocs.messages

import com.overviewdocs.models.{CloneJob,CsvImport,DocumentCloudImport,FileGroup}

/** Background tasks that must be serialized on a document set.
  *
  * These commands are sent from the web server to the worker. All messages
  * are:
  *
  * * Write-only: There are no return values.
  * * Backed up: The web server has stored the data elsewhere (in the
  *   database), so that if the worker is dead it will infer the message the
  *   next time it starts up.
  * * Serialized: on a given document set, only one message will be processed
  *   at a time. Messages are processed in FIFO order.
  */
object DocumentSetCommands {
  sealed trait Command { val documentSetId: Long }

  /** A special kind of command that is passed to workers immediately.
    *
    * We haven't figured out the exact semantics yet....
    */
  sealed trait CancelCommand extends Command

  /** Empty all GroupedFileUploads from the given FileGroup, add the resulting
    * Documents to the DocumentSet, then delete the FileGroup.
    *
    * Stored as a FileGroup.
    */
  case class AddDocumentsFromFileGroup(fileGroup: FileGroup) extends Command {
    override val documentSetId = fileGroup.addToDocumentSetId.get
  }

  /** Parse what's left of a CsvImport, add the resulting Documents to the
    * DocumentSet, and lo_unlink() the file.
    *
    * Stored as a CsvImport.
    */
  case class AddDocumentsFromCsvImport(csvImport: CsvImport) extends Command {
    override val documentSetId = csvImport.documentSetId
  }

  /** Create Documents out of documents/pages on DocumentCloud, then delete the
    * DocumentCloudImport.
    *
    * Stored as DocumentCloudImport.
    */
  case class AddDocumentsFromDocumentCloud(documentCloudImport: DocumentCloudImport) extends Command {
    override val documentSetId = documentCloudImport.documentSetId
  }

  /** Copy user data from one DocumentSet to another.
    *
    * Stored as CloneJob.
    */
  case class CloneDocumentSet(cloneJob: CloneJob) extends Command {
    override val documentSetId = cloneJob.destinationDocumentSetId
  }

  /** Delete a DocumentSet and all associated information.
    *
    * Stored in the database as document_set.deleted.
    */
  case class DeleteDocumentSet(documentSetId: Long) extends Command

  /** Complete all computations surrounding an AddDocumentsFromFileGroup as soon
    * as possible.
    *
    * This Command is different from the rest: it is *not serialized*. As soon
    * as the broker receives this Command, it will forward a cancel message to
    * all workers and purge the associated Job from its own memory (if it's
    * pending).
    *
    * Stored in the database as file_group.deleted
    */
  case class CancelAddDocumentsFromFileGroup(documentSetId: Long, fileGroupId: Long) extends CancelCommand
}
