package org.overviewproject.jobhandler

// Message to notify parent actor about job status 
// The jobEntityId is fileGroupId or documentSetId. Eventually they will
// be one.

object JobProtocol {
  case class JobDone(jobEntityId: Long)
  case class JobStart(jobEntityId: Long)
}