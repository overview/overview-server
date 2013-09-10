package org.overviewproject.jobhandler

import play.api.libs.json._

import org.overviewproject.jobhandler.FileGroupJobHandlerProtocol.ProcessFileCommand



trait ConvertMessage {
  protected case class Message(cmd: String, args: JsValue)
  
  implicit private val messageReads = Json.reads[Message]
  
  protected def getMessage(message: String): Message = Json.parse(message).as[Message]
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