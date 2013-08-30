package org.overviewproject.jobhandler

import play.api.libs.json._

import org.overviewproject.jobhandler.JobHandlerProtocol.{ DeleteCommand, SearchCommand }
import org.overviewproject.jobhandler.FileGroupJobHandlerProtocol.ProcessFileCommand


trait ConvertMessage {
  protected case class Message(cmd: String, args: JsValue)
  
  implicit private val messageReads = Json.reads[Message]
  
  protected def getMessage(message: String): Message = Json.parse(message).as[Message]
}

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

object ConvertFileGroupMessage extends ConvertMessage {
  private val ProcessFileCmdMsg = "process_file"

  private val processFileCommandReads = Json.reads[ProcessFileCommand]

  def apply(message: String): Any = {
    val m = getMessage(message)

    m.cmd match {
      case ProcessFileCmdMsg => processFileCommandReads.reads(m.args).get
    }

  }

}