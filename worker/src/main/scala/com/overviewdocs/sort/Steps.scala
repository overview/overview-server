package com.overviewdocs.sort

import akka.stream.scaladsl.Source
import akka.stream.Materializer
import java.nio.file.Path
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}

/** Individual, testable steps of external sort. */
private[sort] object Steps {
  /** Stream Records into sorted PageOnDisk instances.
    *
    * onProgress() will be called _during_ streaming. Back-pressure
    * will delay calls to onProgress().
    *
    * @param records Input records.
    * @param tempDirectory Where the PageOnDisk files should be.
    * @param maxNBytesInMemory Maximum size of a PageOnDisk file. (Records will
    *                          be sorted in-memory up to this size.)
    * @param onProgress Callback called with (nRecords, nBytesRead) during read.
    * @param callOnProgressEveryNRecords How often to call onProgress.
    * @param mat Materializer (for reading records).
    * @param blockingEc ExecutionContext to use for file I/O.
    */
  def recordsToPages(
    records: Source[Record, _],
    tempDirectory: Path,
    maxNBytesInMemory: Int,
    onProgress: (Int, Int) => Unit,
    callOnProgressEveryNRecords: Int
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Future[immutable.Seq[PageOnDisk]] = ???

  /** Repeatedly call mergePages() on the smallest M pages, until fewer than M
    * pages remain.
    *
    * @param pagesOnDisk Pages to merge.
    * @param tempDirectory Where the PageOnDisk files should be.
    * @param mergeFactor Number of pages to merge at a time.
    * @param onProgress Called with (nRecordsMerged) during merge. (The same
    *                   record may be merged multiple times.)
    * @param callOnProgressEveryNRecords How often to call onProgress.
    * @param mat Materializer (for reading records).
    * @param blockingEc ExecutionContext to use for file I/O.
    */
  def mergePagesUntilMRemain(
    pagesOnDisk: immutable.Seq[PageOnDisk],
    tempDirectory: Path,
    mergeFactor: Int,
    onProgress: Int => Unit,
    callOnProgressEveryNRecords: Int
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Future[immutable.Seq[PageOnDisk]] = ???

  /** Return a single Source that produces the sorted output of all pages.
    *
    * onProgress() will be called _during_ streaming. Back-pressure
    * will delay calls to onProgress().
    *
    * @param pagesOnDisk Pages to merge.
    * @param onProgress Called with (nRecordsMerged) during merge. (Each Record
    *                   will be merged exactly once.)
    * @param callOnProgressEveryNRecords How often to call onProgress.
    * @param mat Materializer (for reading records).
    * @param blockingEc ExecutionContext to use for file I/O.
    */
  def mergeAllPagesAtOnce(
    pagesOnDisk: immutable.Seq[PageOnDisk],
    onProgress: Int => Unit,
    callOnProgressEveryNRecords: Int
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Source[Record, _] = ???

  /** Returns how many record merges will be required, given PageOnDisk sizes.
    *
    * @param recordCounts: Sizes of input pages.
    * @param mergeFactor: How many input pages we merge at a time.
    */
  def calculateNMerges(recordCounts: immutable.Seq[Int], mergeFactor: Int): Int = {
    val nRecords: Int = recordCounts.fold(0)(_ + _)
    (nRecords * Math.log(recordCounts.size.toDouble) / Math.log(mergeFactor.toDouble)).toInt // FIXME test this!
  }

  /** Calls `mergePagesUntilMRemain()` and then `mergeAllPagesAtOnce()`.
    *
    * onProgress() will be called _during_ streaming. Back-pressure
    * will delay calls to onProgress().
    *
    * @param pagesOnDisk Pages to merge.
    * @param tempDirectory Where the PageOnDisk files should be.
    * @param mergeFactor Number of pages to merge at a time.
    * @param onProgress Called with (nRecordsMerged, nRecordMergesTotal)
    *                   during merge. (The same record may be merged multiple
    *                   times.)
    * @param callOnProgressEveryNRecords How often to call onProgress.
    * @param mat Materializer (for reading records).
    * @param blockingEc ExecutionContext to use for file I/O.
    */
  def mergePages(
    pagesOnDisk: immutable.Seq[PageOnDisk],
    tempDirectory: Path,
    mergeFactor: Int,
    onProgress: (Int, Int) => Unit,
    callOnProgressEveryNRecords: Int
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Source[Record, _] = {
    val nMerges: Int = calculateNMerges(pagesOnDisk.map(_.nRecords), mergeFactor)

    def onProgressPhase1(nMerged: Int): Unit = onProgress(nMerged, nMerges)
    def onProgressPhase2(nMerged: Int): Unit = onProgress(nMerges - nMerged, nMerged)

    val futureSource = for {
      mPages <- mergePagesUntilMRemain(pagesOnDisk, tempDirectory, mergeFactor, onProgressPhase1, callOnProgressEveryNRecords)
    } yield mergeAllPagesAtOnce(mPages, onProgressPhase2, callOnProgressEveryNRecords)

    Source.fromFutureSource(futureSource)
  }

  /** Converts a RecordSource to an Array of record IDs.
    */
  def recordSourceToIdArray(recordSource: RecordSource)(implicit mat: Materializer): Future[Array[Int]] = ???
}
