package org.overviewproject.runner

import scala.concurrent.{ExecutionContext, Future}

/** Manages lots of daemons. */
class SuperServer[A <: DaemonProcess](val daemons: Set[A])(implicit executor: ExecutionContext) {
  /** The set of all DaemonProcesses that have not completed. */
  def notCompleted: Set[A] = daemons.filter(!_.waitFor.isCompleted)

  /** The set of all DaemonProcesses that have completed. */
  def completed : Set[(A, Int)] = daemons.flatMap(d => d.waitFor.value.map(tryI => (d -> tryI.get)))

  private val futurePairs : Seq[Future[(A,Int)]] = daemons.toSeq.map(d => d.waitFor.map(i => (d, i)))

  val waitForFirst : Future[(A,Int)] = Future.firstCompletedOf(futurePairs)

  val waitForAll : Future[Seq[(A,Int)]] = Future.sequence(futurePairs)
}
