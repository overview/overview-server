package org.overviewproject.jobhandler

// Message to notify parent actor that a file has been processed
// The jobEntityId is fileGroupId or documentSetId. Eventually they will
// be one.
case class JobDone(jobEntityId: Long)