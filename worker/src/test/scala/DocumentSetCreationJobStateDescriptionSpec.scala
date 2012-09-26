
package overview.util

import org.specs2.mutable.Specification
import overview.util.DocumentSetCreationJobStateDescription._

class DocumentSetCreationJobStateDescriptionSpec extends Specification {

  "DocumentSetCreationJobStateDescription" should {

    "convert states with no arguments to key string" in {

      OutOfMemory.toString must be equalTo("out_of_memory")
      WorkerError.toString must be equalTo("worker_error")
      Retrieving.toString must be equalTo("retrieving_documents")
      Clustering.toString must be equalTo("clustering")
      Saving.toString must be equalTo("saving_document_tree")
      Done.toString must be equalTo("job_complete")
    }

    "convert states with argument" in {
      ClusteringLevel(4).toString must be equalTo("clustering_level:4")
    }
  }
}
