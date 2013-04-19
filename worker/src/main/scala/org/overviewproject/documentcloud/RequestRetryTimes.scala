package org.overviewproject.documentcloud

import scala.concurrent.duration._
import scala.language.postfixOps
  
/**
 * Provide times to wait before retrying a documentcloud request
 */
trait RequestRetryTimes {
  protected val times: Seq[FiniteDuration]
  
  /**
   * Return a wait duration for the specified attempt.
   * @return None if no more attempts should be made
   */
  def apply(attempt: Int): Option[FiniteDuration] = times.lift(attempt)

}
  

/** Provide default retry times */
object RequestRetryTimes {
  private val DefaultTimes: Seq[FiniteDuration] = Seq(
    1 second,
    30 seconds,
    1 minute
  ) 
  
  def apply(): RequestRetryTimes = new RequestRetryTimes { 
    protected override val times = DefaultTimes 
  }
}
  