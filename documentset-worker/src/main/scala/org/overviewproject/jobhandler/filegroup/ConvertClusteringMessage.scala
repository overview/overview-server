package org.overviewproject.jobhandler.filegroup

import org.overviewproject.jobhandler.ConvertMessage
import play.api.libs.json.Json
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol._

object ConvertClusteringMessage extends ConvertMessage {
  private val StartClusteringCmdMsg = "start_clustering"

  private val startClusteringCommandReads = Json.reads[StartClusteringCommand]
  
  def apply(message: String): Command = {
    val m = getMessage(message)
    
    m.cmd match { 
      case StartClusteringCmdMsg => startClusteringCommandReads.reads(m.args).get
    }
  }
}