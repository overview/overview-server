package org.overviewproject.util

import org.overviewproject.util.DocumentSetCreationJobStateDescription._

// Returns a key for the error message to display to the user, to be internationalized 
// keys in conf/messages under views.DocumentSet._documentSet.job_state_description 
object ExceptionStatusMessage {

  def apply(t: Throwable): String = t  match {
    case d: DisplayedError => d.toString
    case e: java.lang.OutOfMemoryError => "out_of_memory"
    case _ => "worker_error"
  }
}
