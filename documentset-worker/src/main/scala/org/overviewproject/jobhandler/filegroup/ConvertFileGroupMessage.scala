package org.overviewproject.jobhandler.filegroup

import org.overviewproject.jobhandler.ConvertMessage
import play.api.libs.json.Json
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ProcessFileCommand



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