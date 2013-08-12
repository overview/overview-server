package org.overviewproject.jobhandler

import org.overviewproject.jobhandler.JobHandlerProtocol.SearchCommand
import play.api.libs.json._
import org.overviewproject.jobhandler.JobHandlerProtocol.DeleteCommand


/** Converts messages from the queue into specific Command Messages */
object ConvertMessage {
  private val SearchCmdMsg = "search"
  private val DeleteCmdMsg = "delete"
    
  private case class Message(cmd: String, args: JsValue)
  
  implicit private val searchCommandReads = Json.reads[SearchCommand]
  implicit private val deleteCommandReads = Json.reads[DeleteCommand]
  implicit private val searchMessageReads = Json.reads[Message]
  
  // Once we have more than one command, we'll actually have to look at the 
  // message to decide how to convert it.
  def apply(message: String): Command = {
    val m = Json.parse(message).as[Message]
    
    m.cmd match {
      case SearchCmdMsg => m.args.as[SearchCommand]
      case DeleteCmdMsg => m.args.as[DeleteCommand]
    }

  }
}