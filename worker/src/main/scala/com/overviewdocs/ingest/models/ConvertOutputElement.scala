package com.overviewdocs.ingest.models

sealed trait ConvertOutputElement
object ConvertOutputElement {
  case class ToProcess(writtenFile2: WrittenFile2) extends ConvertOutputElement
  case class ToIngest(processedFile2: ProcessedFile2) extends ConvertOutputElement
}
