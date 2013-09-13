package org.overviewproject.tree

object DocumentSetCreationJobType extends Enumeration {
  // Constants copied from "document_set_creation_job_type" table
  val DocumentCloud = Value(1, "DocumentCloud")
  val CsvUpload = Value(2, "CsvUpload")
  val Clone = Value(3, "Clone")
  val FileUpload = Value(4, "FileUpload")
}
