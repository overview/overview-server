package com.overviewdocs.sort

import akka.stream.Materializer
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}

/** Sort large collections of Records, with progress reporting.
  *
  * This uses an external merge sort, which means for R records:
  *
  * 1) create P sorted "pages" of records and write them to disk.
  * 2) while there are more than M pages, merge the smallest M pages into a new,
  *    larger page and delete them.
  * 3) merge all pages, generating an Array of IDs.
  *
  * We simplify progress reporting by noticing that all records are written to
  * and read from the disk several times: `1 + log_M(P)`. (Disk reads/writes may
  * not be the bottleneck, but we assume their cost is _proportional_ to the
  * bottleneck.) We assume the first read+sort is far more costly than the rest;
  * specify the factor in SortConfig.
  */
class Sorter(val config: SortConfig) {
  /** Returns sorted IDs, using temporary files.
    *
    * By the time the Future is resolved, all temporary files will be deleted.
    */
  def sortIds(
    recordSource: RecordSource,
    onProgress: Double => Unit
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Future[immutable.Seq[Int]] = {
    var lastReadPassProgress: Double = 0.0

    def onProgressReadPass(nRead: Int, nBytesRead: Long): Unit = {
      // How many bytes will be on disk once we read all bytes of input?
      val nBytesInputEst: Double = nBytesRead.toDouble * recordSource.nRecords / nRead

      // How many bytes will we read during merging? Each merge is a write+read
      val nPages = (nBytesInputEst / config.maxNBytesInMemory).ceil
      val nMergePassesEst: Int = 1 + (Math.log(nPages) / Math.log(config.mergeFactor.toDouble)).ceil.toInt
      val nBytesMergeEst: Double = nMergePassesEst * nBytesInputEst

      val totalCost = nBytesInputEst * config.firstReadCostBoost + nBytesMergeEst
      val progress = nBytesRead * config.firstReadCostBoost / totalCost
      onProgress(progress)

      lastReadPassProgress = progress
    }

    def onProgressMergePass(nMerged: Int, nMergesTotal: Int): Unit = {
      val mergeProgress: Double = nMerged.toDouble / nMergesTotal
      val remaining = (1.0 - mergeProgress) * (1.0 - lastReadPassProgress)
      val progress = 1.0 - remaining
      onProgress(progress)
    }

    Steps.recordsToPages(
      recordSource.records,
      config.tempDirectory,
      config.maxNBytesInMemory,
      onProgressReadPass,
      config.firstReadNRecordsPerProgressCall
    ).flatMap { lotsOfPages =>
      val mergedRecords = Steps.mergePages(
        lotsOfPages,
        config.tempDirectory,
        config.mergeFactor,
        onProgressMergePass, 
        config.mergeNRecordsPerProgressCall
      )
      Steps.recordSourceToIds(mergedRecords)
    }
  }
}
