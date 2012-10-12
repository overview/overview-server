package overview.util

import java.sql.Connection
import persistence.DocumentSetCleaner
import persistence.PersistentDocumentSetCreationJob
import persistence.DocumentSetCreationJobState._

class JobRestarter(cleaner: DocumentSetCleaner) {

  def restart(jobs: Seq[PersistentDocumentSetCreationJob])(implicit c: Connection) {
    jobs.map { j => 
      cleaner.clean(j.documentSetId)
      j.state = Submitted
      j.update
      
    }
  }
}
