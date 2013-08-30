package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.JobHandlerProtocol.{ DeleteCommand, SearchCommand }


class ConvertMessageSpec extends Specification {

  "ConvertMessage" should {

    "convert a search command" in {
      val documentSetId = 123
      val queryString = "project:5239 search terms"

      val messageString = s"""{
        "cmd": "search",
        "args": { 
          "documentSetId": $documentSetId,
          "query": "$queryString"
        }
     }"""

      val command = ConvertDocumentSetMessage(messageString)

      command must beLike { case SearchCommand(documentSetId, queryString) => ok }

    }

    "convert a delete command" in {
      val documentSetId = 123

      val messageString = s"""{
        "cmd": "delete",
        "args": {
          "documentSetId": $documentSetId
        }
      }"""

      val command = ConvertDocumentSetMessage(messageString)
      
      command must beLike { case DeleteCommand(documentSetId) => ok}
    }

  }

}