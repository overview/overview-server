package org.overviewproject.tree.orm

object FileJobState extends Enumeration {
  type FileJobState = Value
  
  val Complete = Value(1, "Complete")
  val InProgress = Value(2, "InProgress")
  val Error = Value(3, "Error")
}