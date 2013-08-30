package org.overviewproject.jobhandler

import play.api.libs.json._

import org.overviewproject.jobhandler.JobHandlerProtocol.{ DeleteCommand, SearchCommand }
import org.overviewproject.jobhandler.FileGroupJobHandlerProtocol.ProcessFileCommand

/** Converts messages from the queue into specific Command Messages */
object ConvertMessage {
  private val SearchCmdMsg = "search"
  private val DeleteCmdMsg = "delete"
  private val ProcessFileCmdMsg = "process_file"
    
  private case class Message(cmd: String, args: JsValue)
  
  implicit private val searchCommandReads = Json.reads[SearchCommand]
  implicit private val deleteCommandReads = Json.reads[DeleteCommand]
  implicit private val searchMessageReads = Json.reads[Message]
  implicit private val processFileCommandReads = Json.reads[ProcessFileCommand]
  
  def apply(message: String): Any = {
    val m = Json.parse(message).as[Message]
    
    m.cmd match {
      case SearchCmdMsg => m.args.as[SearchCommand]
      case DeleteCmdMsg => m.args.as[DeleteCommand]
      case ProcessFileCmdMsg => m.args.as[ProcessFileCommand]
    }

  }
}