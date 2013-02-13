package org.overviewproject.util

import overview.util.Progress._

class ThrottledProgressReporter {

  type UpdateFn = Progress => Unit
  
  private var stateChangeReceivers: Seq[UpdateFn] = Seq.empty
    
  def notifyOnStateChange(receiver: UpdateFn) {
    stateChangeReceivers +:= receiver
  }
  
  def update(progress: Progress) {
    stateChangeReceivers.foreach(_(progress))
  }
}