package com.overviewdocs.jobhandler.filegroup

import play.api.libs.json.Json
import com.overviewdocs.messagequeue.ConvertMessage
import com.overviewdocs.jobhandler.filegroup.FileGroupJobMessages._


object ConvertClusteringMessage extends ConvertMessage {
    
  private val ClusterFileGroupCmdMsg = "cluster_file_group"
  private val CancelFileUploadCmd = "cancel_file_upload"
    
  private val clusterFileGroupCommandReads = Json.reads[ClusterFileGroupCommand]
  private val cancelFileUploadCommandReads = Json.reads[CancelClusterFileGroupCommand]
  
  def apply(message: String): Command = {
    val m = getMessage(message)

    m.cmd match { 
      case ClusterFileGroupCmdMsg => clusterFileGroupCommandReads.reads(m.args).get
      case CancelFileUploadCmd => cancelFileUploadCommandReads.reads(m.args).get
    }
  }
}