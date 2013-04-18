package org.overviewproject.documentcloud

import scala.concurrent.duration._
import scala.language.postfixOps
  
trait RequestRetryTimes {
  protected val times: Seq[FiniteDuration]
  
  def apply(attempt: Int): Option[FiniteDuration] = times.lift(attempt)

}
  

object RequestRetryTimes {
  private val DefaultTimes: Seq[FiniteDuration] = Seq(
    1 second,
    1 minute, 
    3 minutes
  ) 
  
  def apply(): RequestRetryTimes = new RequestRetryTimes { 
    protected override val times = DefaultTimes 
  }
}
  