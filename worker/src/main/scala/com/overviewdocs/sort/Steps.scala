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

  /** like seq.reduce(), but calls fn as few times as possible.
    *
    * Scala's `Seq(1,2,3,4,5,6,7,8).reduce(fn)` will expand to
    * fn(1,fn(2,fn(3,fn(4,fn(5,fn(6,fn(7,8))))))).
    *
    * _This_ method is more balanced:
    * fn(fn(fn(1,2),fn(3,4)),fn(fn(5,6),fn(7,8))).
    *
    * [adam, 2017-06-21] I'm not sure whether balancing leads to efficiency.
    * I believe it does, intuitively, since multiple fn()s can run concurrently.
    */
  private def reduceEfficient[A](seq: immutable.IndexedSeq[A])(fn: (A, A) => A): A = {
    seq.size match {
      case 0 => ???
      case 1 => seq(0)
      case 2 => fn(seq(0), seq(1))
      case n => fn(reduceEfficient(seq.slice(0, n / 2))(fn), reduceEfficient(seq.slice(n / 2, n))(fn))
    }
  }

  private def waitForUnits(left: Future[Unit], right: Future[Unit])(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- left
      _ <- right
    } yield ()
  }

  /** Return a single RecordSource that produces the sorted output of all pages.
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
  )(implicit mat: Materializer, blockingEc: ExecutionContext): RecordSource = {
    val nRecords = pagesOnDisk.map(_.nRecords).reduce(_ + _)
    RecordSource(
      nRecords,
      reduceEfficient(pagesOnDisk.map(_.toSourceDestructive).toIndexedSeq)(_.mergeSortedMat(_)(waitForUnits))
        .zipWithIndex.map { t: Tuple2[Record,Long] =>
          val i = t._2.toInt + 1
          if (i % callOnProgressEveryNRecords == 0 || i == nRecords) {
            onProgress(i)
          }
          t._1
        }
    )
  }

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
  )(implicit mat: Materializer, blockingEc: ExecutionContext): RecordSource = {
    val nMerges: Int = calculateNMerges(pagesOnDisk.map(_.nRecords), mergeFactor)

    def onProgressPhase1(nMerged: Int): Unit = onProgress(nMerged, nMerges)
    def onProgressPhase2(nMerged: Int): Unit = onProgress(nMerges - nMerged, nMerged)

    val futureSource = for {
      mPages <- mergePagesUntilMRemain(pagesOnDisk, tempDirectory, mergeFactor, onProgressPhase1, callOnProgressEveryNRecords)
    } yield mergeAllPagesAtOnce(mPages, onProgressPhase2, callOnProgressEveryNRecords).records

    RecordSource(
      pagesOnDisk.map(_.nRecords).reduce(_ + _),
      Source.fromFutureSource(futureSource)
        .mapMaterializedValue(f => f.flatMap(identity)) // Future[Future[Unit]] => Future[Unit]
    )
  }

  /** Converts a RecordSource to an Array of record IDs.
    */
  def recordSourceToIdArray(recordSource: RecordSource)(implicit mat: Materializer): Future[Array[Int]] = ???
}
