package org.overviewproject.jobhandler.filegroup

import org.overviewproject.messagequeue.ConvertMessage
import play.api.libs.json.Json
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol._


object ConvertClusteringMessage extends ConvertMessage {
  private val StartClusteringCmdMsg = "start_clustering"
    
  private val ClusterFileGroupCmdMsg = "cluster_file_group"
  private val CancelUploadWithDocumentSetCmd = "cancel_upload_with_document_set"
  private val CancelUploadCmd = "cancel_upload"
    
  private val startClusteringCommandReads = Json.reads[StartClusteringCommand]
  private val clusterFileGroupCommandReads = Json.reads[ClusterFileGroupCommand]
  private val cancelUploadWithDocumentSetCommandReads = Json.reads[CancelUploadWithDocumentSetCommand]
  private val cancelUploadCommandReads = Json.reads[CancelUploadCommand]
  
  def apply(message: String): Command = {
    val m = getMessage(message)

    m.cmd match { 
      case StartClusteringCmdMsg => startClusteringCommandReads.reads(m.args).get
      case ClusterFileGroupCmdMsg => clusterFileGroupCommandReads.reads(m.args).get
      case CancelUploadWithDocumentSetCmd => cancelUploadWithDocumentSetCommandReads.reads(m.args).get
      case CancelUploadCmd => cancelUploadCommandReads.reads(m.args).get
    }
  }
}