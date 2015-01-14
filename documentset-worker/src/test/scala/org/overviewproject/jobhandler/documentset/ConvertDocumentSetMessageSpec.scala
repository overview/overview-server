package org.overviewproject.jobhandler.documentset

import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol._

class ConvertDocumentSetMessageSpec extends Specification {

  "ConvertMessage" should {

    "convert a delete command" in {
      val documentSetId = 123
      val waitFlag = true

      val messageString = s"""{
        "cmd": "delete",
        "args": {
          "documentSetId": $documentSetId,
          "waitForJobRemoval": $waitFlag
        }
      }"""

      val command = ConvertDocumentSetMessage(messageString)

      command must beLike { case DeleteCommand(documentSetId, waitFlag) => ok }
    }

    "convert a deleteTreeJob commmand" in {
      val jobId = 123

      val messageString = s"""{
        "cmd": "delete_tree_job",
        "args": {
           "jobId": $jobId
        }
      }"""

      val command = ConvertDocumentSetMessage(messageString)

      command must beLike { case DeleteTreeJobCommand(jobId) => ok }

    }

  }

}
