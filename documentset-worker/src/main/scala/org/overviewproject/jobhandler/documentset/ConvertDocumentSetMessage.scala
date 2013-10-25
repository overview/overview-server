package org.overviewproject.jobhandler.documentset

import play.api.libs.json._
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol.{ DeleteCommand, SearchCommand }
import org.overviewproject.messagequeue.ConvertMessage




/** Converts messages from the queue into specific Command Messages */
object ConvertDocumentSetMessage extends ConvertMessage {
  private val SearchCmdMsg = "search"
  private val DeleteCmdMsg = "delete"


  private val searchCommandReads = Json.reads[SearchCommand]
  private val deleteCommandReads = Json.reads[DeleteCommand]
  
  def apply(message: String): Command = {
    val m = getMessage(message)

    m.cmd match {
      case SearchCmdMsg => searchCommandReads.reads(m.args).get
      case DeleteCmdMsg => deleteCommandReads.reads(m.args).get
    }

  }
}
