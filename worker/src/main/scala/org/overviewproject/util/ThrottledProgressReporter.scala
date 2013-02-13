package org.overviewproject.util

import overview.util.Progress._

class ThrottledProgressReporter(val stateChange: Seq[Progress => Unit], val interval: Seq[Progress => Unit], updateInterval: Long = 500l) {

  type UpdateFn = Progress => Unit
  private val SignificantProgressChange: Double = 0.01

  private var previouslyReportedProgress: Option[Progress] = None
  private var nextUpdateTime: Long = now

  def update(progress: Progress) {

    if (significantChange(progress)) {
      stateChange.foreach(_(progress))
      previouslyReportedProgress = Some(progress)
    }

    if (intervalPassed) {
      interval.foreach(_(progress))
      nextUpdateTime = now + updateInterval
    }
  }

  private def intervalPassed: Boolean = now >= nextUpdateTime

  private def significantChange(progress: Progress): Boolean =
    previouslyReportedProgress.map { p =>
      !progress.status.sameStateAs(p.status) || (progress.fraction - p.fraction).abs >= SignificantProgressChange
    } getOrElse (true)

  private def now: Long = scala.compat.Platform.currentTime
}