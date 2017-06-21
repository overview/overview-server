package com.overviewdocs.sort

import akka.stream.Materializer
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
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Future[Array[Int]] = {
    def onProgressReadPass(nRead: Int, nBytesRead: Int): Unit = ???
    def onProgressMergePass(nMerged: Int, nMergesTotal: Int): Unit = ???

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
      Steps.recordSourceToIdArray(mergedRecords)
    }
  }
}
