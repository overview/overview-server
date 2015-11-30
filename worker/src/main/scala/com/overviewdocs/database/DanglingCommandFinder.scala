package com.overviewdocs.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.tables.{CloneJobs,CsvImports,DocumentCloudImports,DocumentSets,FileGroups}

/** Finds Commands that should be started when the app loads.
  *
  * A dangling Command is left behind by a terminated worker.
  */
object DanglingCommandFinder extends HasDatabase {
  import database.api._

  def run: Future[Seq[DocumentSetCommands.Command]] = {
    for {
      c1 <- findCloneJobCommands
      c2 <- findCsvImportCommands
      c3 <- findDocumentCloudImportCommands
      c4 <- findFileGroupCommands
      c5 <- findDeleteDocumentSetCommands
    } yield c1 ++ c2 ++ c3 ++ c4 ++ c5
  }

  private def findCloneJobCommands = {
    database.seq(CloneJobs).map(_.map(DocumentSetCommands.CloneDocumentSet.apply _))
  }

  private def findCsvImportCommands = {
    database.seq(CsvImports).map(_.map(DocumentSetCommands.AddDocumentsFromCsvImport.apply _))
  }

  private def findDocumentCloudImportCommands = {
    database.seq(DocumentCloudImports).map(_.map(DocumentSetCommands.AddDocumentsFromDocumentCloud.apply _))
  }

  private def findFileGroupCommands = {
    database.seq(
      FileGroups.filter(_.addToDocumentSetId.nonEmpty)
    ).map(_.map(DocumentSetCommands.AddDocumentsFromFileGroup.apply _))
  }

  private def findDeleteDocumentSetCommands = {
    database.seq(DocumentSets.filter(_.deleted).map(_.id)).map(_.map(DocumentSetCommands.DeleteDocumentSet.apply _))
  }
}
