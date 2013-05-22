package org.overviewproject.jobhandler

import org.overviewproject.jobhandler.JobHandlerProtocol.SearchCommand
import play.api.libs.json._

object ConvertMessage {
  private case class Message(cmd: String, args: SearchCommand)
  
  implicit private val searchCommandReads = Json.reads[SearchCommand]
  implicit private val searchMessageReads = Json.reads[Message]
  
  def apply(message: String): SearchCommand = {
    val m = Json.parse(message).as[Message]
    m.args
  }
}