package org.overviewproject.util

import overview.util.Progress._

class ThrottledProgressReporter {

  type UpdateFn = Progress => Unit
  private val SignificantProgressChange: Double = 0.1
  
  private var stateChangeReceivers: Seq[UpdateFn] = Seq.empty
  private var previouslyReportedProgress: Option[Progress] = None
  
  def notifyOnStateChange(receiver: UpdateFn) {
    stateChangeReceivers +:= receiver
  }
  
  def update(progress: Progress) {
    
    if (significantChange(progress)) {
      stateChangeReceivers.foreach(_(progress))
      previouslyReportedProgress = Some(progress)
    }
  }
  
  private def significantChange(progress: Progress): Boolean =
    previouslyReportedProgress.map { p =>
      progress.status != p.status || (progress.fraction - p.fraction).abs >= SignificantProgressChange
    } getOrElse(true)

}