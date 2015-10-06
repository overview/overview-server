package com.overviewdocs.messages

/** Commands that relate to clustering.
  *
  * Truly, each of these should be in DocumentSetCommands and not here. This
  * class is just an interim step. If you're reading this message, you should
  * take the time to tidy up and nix this class entirely :).
  */
object ClusterCommands {
  sealed trait Command

  case class ClusterFileGroup(documentSetId: Long, fileGroupId: Long) extends Command
  case class CancelFileUpload(documentSetId: Long, fileGroupId: Long) extends Command
}
