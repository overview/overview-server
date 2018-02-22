package com.overviewdocs.ingest.pipeline

import akka.util.ByteString

/** Message explaining why there will be no more StepOutputFragments.
  */
sealed trait StepOutputEnd
object StepOutputEnd {
  /** The File2 has been processed correctly and entirely. All child File2s
    * have been transmitted.
    */
  case object Done extends StepOutputEnd

  /** The user canceled the Step2 before it finished producing child File2s.
    *
    * The last-transmitted File2 should not be considered `WRITTEN`, even if a
    * lot of fragments arrived for it.
    */
  case object Canceled extends StepOutputEnd

  /** A StepStep determined the input File2 was invalid.
    *
    * In other words: a normal error.
    *
    * The Step's invoker can decide what to do with the data that has
    * already been passed. It makes sense to an end user: for instance, if the
    * input is a truncated CSV or Zip it would be nice to show the user all the
    * valid documents that were produced before the error.
    */
  case class FileError(message: String) extends StepOutputEnd

  /** A StepLogic crashed, and an email was sent to Overview developers.
    *
    * In other words: a bug in Overview.
    *
    * The Step's invoker can decide what to do with the data that has
    * already been passed. It makes sense to an end user: for instance, if the
    * input is a truncated CSV or Zip it would be nice to show the user all the
    * valid documents that were produced before the error.
    *
    * A note about graceful deploys: if a StepStep relies upon external
    * daemons, the StepStep and daemons will arrange themselves such that
    * a daemon restart or scale-down does not cause a StepError.
    */
  case class StepError(exception: Exception) extends StepOutputEnd
}
