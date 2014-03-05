package models.orm.finders

import org.overviewproject.tree.orm.finders.{ BaseDocumentSetCreationJobTreeFinder, FinderResult }
import models.orm.Schema.documentSetCreationJobTrees
import org.overviewproject.tree.orm.DocumentSetCreationJobTree

object DocumentSetCreationJobTreeFinder extends BaseDocumentSetCreationJobTreeFinder(documentSetCreationJobTrees) {

  type DocumentSetCreationJobTreeFinderResult = FinderResult[DocumentSetCreationJobTree]
  
  def byJob(jobId: Long): DocumentSetCreationJobTreeFinderResult = 
    byJobQuery(jobId)
  

}