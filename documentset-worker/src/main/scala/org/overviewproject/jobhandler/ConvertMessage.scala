package org.overviewproject.jobhandler

import org.overviewproject.jobhandler.JobHandlerProtocol.SearchCommand
import play.api.libs.json._


/** Converts messages from the queue into specific Command Messages */
object ConvertMessage {
  private case class Message(cmd: String, args: SearchCommand)
  
  implicit private val searchCommandReads = Json.reads[SearchCommand]
  implicit private val searchMessageReads = Json.reads[Message]
  
  // Once we have more than one command, we'll actually have to look at the 
  // message to decide how to convert it.
  def apply(message: String): SearchCommand = {
    val m = Json.parse(message).as[Message]
    m.args
  }
}