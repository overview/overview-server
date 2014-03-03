package org.overviewproject.runner

import scala.concurrent.Future

import org.overviewproject.runner.commands.Command

/** Something that runs in the background. */
trait DaemonProcess {
  /** Asynchronously tells the daemon to stop.
    *
    * Usage:
    *   daemon.destroy()
    *   val statusCode = Await(daemon.waitFor, 100 milliseconds)
    */
  def destroy: Unit

  /** Returns the status code (eventually) from the daemon.
    *
    * Implementation note: remember to use scala.concurrent.blocking{} in
    * implementations: there may be several waitFor() commands running in
    * parallel, and if we don't mark them blocking they will starve the thread
    * pool, preventing us from detecting results.
    */
  def waitFor: Future[Int]
}
