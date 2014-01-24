
package org.overviewproject.util

import org.specs2.mutable.Specification
import org.overviewproject.util.DocumentSetCreationJobStateDescription._

class DocumentSetCreationJobStateDescriptionSpec extends Specification {

  "DocumentSetCreationJobStateDescription" should {

    "convert states with no arguments to key string" in {

      Clustering.toString must be equalTo("clustering")
      Saving.toString must be equalTo("saving_document_tree")
      Done.toString must be equalTo("job_complete")
    }

    "convert states with argument" in {
      ClusteringLevel(4).toString must be equalTo("clustering_level:4")
      Retrieving(123, 345).toString must be equalTo("retrieving_documents:123:345")
      Parsing(543l, 129402l).toString must be equalTo("parsing_data:543:129402")
    }
  }
}
