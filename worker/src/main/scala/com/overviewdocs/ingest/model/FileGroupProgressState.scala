package com.overviewdocs.ingest.model

import com.google.common.collect.{Range=>GRange,RangeSet,TreeRangeSet}
import java.time.Instant
import scala.concurrent.Promise

import com.overviewdocs.models.{File2,FileGroup}

/** (mutable) Progress towards ingesting a single FileGroup.
  *
  * This is _mutable_, and it's friendly with parallel processing of files.
  * Here's how: we imagine the entire progress bar as a bunch of _sub_-progress
  * bars ("pieces"), one per file. If tasks demand it, each of those pieces can
  * be further sub-divided. Each time a sub-task reports progress, it fills in
  * its own tiny piece of the overall progress bar. We report progress by
  * measuring the completed fraction of the overall progress bar.
  *
  * Usage:
  * {{{
  * val state = new FileGroupProgressState(fileGroup, ...)
  * val progress1 = state.buildFile2Progress(file1)
  * val progress2 = state.buildFile2Progress(file2)
  * progress1.onProgress(0.3)
  * progress2.onProgress(0.5)
  * }}}
  */
class FileGroupProgressState(
  val fileGroup: FileGroup,
  val nFilesAlreadyIngested: Int,
  val nBytesAlreadyProcessed: Long,
  val startedAt: Instant,
  val onChange: FileGroupProgressState => Unit,
  val cancel: Promise[akka.Done]
) {
  /** A nested progress bar, in which ranges from [0.0 .. 1.0] indicate what's
    * completed.
    *
    * For instance, a 100-byte file in a 1kb FileGroup will correspond to the
    * range [0.0 .. 0.1]. To calculate the progress of the entire FileGroup,
    * iterate over the RangeSet and add up sizes.
    */
  private val completedRangeSet: RangeSet[java.lang.Long] = TreeRangeSet.create[java.lang.Long]()

  private var lastAllocatedByte: Long = nBytesAlreadyProcessed
  private var nFilesIngestedAfterStart: Int = 0
  private def nBytesProcessedAfterStart: Long = synchronized {
    var total = 0L
    val it = completedRangeSet.asRanges.iterator
    while (it.hasNext) {
      val range: GRange[java.lang.Long] = it.next
      total += range.upperEndpoint - range.lowerEndpoint
    }
    total
  }

  def setBytesProcessed(beginByte: Long, endBytePlusOne: Long): Unit = {
    synchronized { completedRangeSet.add(GRange.closedOpen(beginByte, endBytePlusOne)) }
    onChange(this)
  }

  def incrementNFilesIngested: Unit = {
    synchronized { nFilesIngestedAfterStart += 1 }
    onChange(this)
  }

  def nFilesIngested: Int = synchronized {
    nFilesAlreadyIngested + nFilesIngestedAfterStart
  }

  def getProgressReport: FileGroupProgressState.ProgressReport = synchronized {
    // Number of bytes is always since last resume
    val nBytes = nBytesProcessedAfterStart // cache computation
    val nBytesTotal = fileGroup.nBytes.get - nBytesAlreadyProcessed
    val inverseFraction = nBytesTotal / scala.math.max(nBytes, 1)
    val elapsedMilli = Instant.now.toEpochMilli - startedAt.toEpochMilli
    val expectedMilli = (elapsedMilli * inverseFraction).toLong
    val estimatedCompletionTime = Instant.ofEpochMilli(startedAt.toEpochMilli + expectedMilli)

    FileGroupProgressState.ProgressReport(
      nFilesIngested,
      nBytesAlreadyProcessed + nBytes,
      estimatedCompletionTime
    )
  }

  /** Returns a new File2Progress, for progress-reporting on the File2's status.
    *
    * This mutates the State.
    *
    * You should call this once for each File2 in the FileGroup: that way, each
    * byte of input will be accounted for.
    */
  def buildFile2Progress(file2: File2): File2Progress = synchronized {
    val fileNBytes = file2.blob.get.nBytes
    val ret = File2Progress(this, file2.id, lastAllocatedByte, lastAllocatedByte + fileNBytes)
    lastAllocatedByte += fileNBytes
    ret
  }
}

object FileGroupProgressState {
  case class ProgressReport(
    nFilesProcessed: Int,
    nBytesProcessed: Long,
    estimatedCompletionTime: Instant
  )
}

/** Completion of a single, root File2.
  *
  * You can either call `.onProgress()` to report progress of the entire File2; or
  * you can call `.split(fractions)` to produce sub-progress meters that can be
  * completed individually.
  */
case class File2Progress(
  fileGroupProgressState: FileGroupProgressState,
  file2Id: Long,

  /** First byte within the FileGroupProgressState. */
  beginByte: Long,

  /** Last byte plus one, within the FileGroupProgressState. */
  endBytePlusOne: Long
) {
  private val d = endBytePlusOne - beginByte

  private def color(begin: Double, end: Double): Unit = {
    // Clamp. Inputs should never be out of range. But [2018-04-10, adamhooper]
    // maybe they are during integration tests on
    // 2849608dd70ab3357f7f01a70a075da1b7420342.
    val saneBegin = Math.min(1.0, Math.max(0.0, Math.min(begin, end)))
    val saneEnd = Math.min(1.0, Math.max(0.0, Math.max(begin, end)))
    // On a lazy Sunday, intrepid interns are encouraged to checkout
    // 2849608dd70ab3357f7f01a70a075da1b7420342, delete the above lines, and
    // figure out what's causing the errors.

    val a = (beginByte + d * saneBegin).toLong
    val b = (beginByte + d * saneEnd).toLong
    fileGroupProgressState.setBytesProcessed(a, b)
  }

  def progressPiece = new ProgressPiece(
    color _,
    0.0,
    1.0
  )
}
