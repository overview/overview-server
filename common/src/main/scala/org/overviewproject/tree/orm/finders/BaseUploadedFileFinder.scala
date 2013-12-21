package org.overviewproject.tree.orm.finders

import scala.language.postfixOps
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ DocumentSet, UploadedFile }
import org.squeryl.{ Query, Table }

class BaseUploadedFileFinder(table: Table[UploadedFile], documentSetsTable: Table[DocumentSet]) extends
DocumentSetRelationFinder(table, documentSetsTable) {
  
  def byDocumentSetQuery(documentSetId: Long): Query[UploadedFile] = 
   relationByDocumentSetComponent(ds => 
      (ds.id === documentSetId) and (ds.uploadedFileId isNotNull),
        ds => ds.uploadedFileId.getOrElse(-1), // FIXME: for some reason, get leads to exceptions  
        uf => uf.id) 

}