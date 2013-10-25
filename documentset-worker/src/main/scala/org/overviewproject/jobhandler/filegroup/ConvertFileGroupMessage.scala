package org.overviewproject.jobhandler.filegroup

import org.overviewproject.messagequeue.ConvertMessage
import play.api.libs.json.Json
import org.overviewproject.jobhandler.filegroup.FileGroupMessageHandlerProtocol._



object ConvertFileGroupMessage extends ConvertMessage {
  private val ProcessFileCmdMsg = "process_file"

  private val processFileCommandReads = Json.reads[ProcessFileCommand]

  def apply(message: String): Command = {
    val m = getMessage(message)

    m.cmd match {
      case ProcessFileCmdMsg => processFileCommandReads.reads(m.args).get
    }

  }

}