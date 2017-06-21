package com.overviewdocs.sort

import akka.stream.scaladsl.{Sink,Source}
import akka.stream.Materializer
import java.nio.file.Path
import scala.collection.{immutable,mutable}
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
  )(implicit mat: Materializer, blockingEc: ExecutionContext): Future[immutable.Seq[PageOnDisk]] = {
    // Progress reporting will set this as a fraction of calculateNMerges(). So
    // make sure the logic in this method is the same as the logic in
    // calculateNMerges().
    var nRecordsMergedBeforeCurrentMerge = 0

    // to merge one "pass". For instance, merging (1)(2)(3)(4)(5) with M=2, this
    // would eventually return (1,2)(3,4)(5).
    def mergeOnePassThenRecurse(thisPassPages: immutable.Seq[PageOnDisk]): Future[immutable.Seq[PageOnDisk]] = {
      if (thisPassPages.size <= mergeFactor) {
        return Future.successful(thisPassPages)
      }

      def mergeOneThenRecurse(thisPassTodo: immutable.Seq[PageOnDisk], nextPass: immutable.Seq[PageOnDisk]): Future[immutable.Seq[PageOnDisk]] = {
        if (thisPassTodo.size < 2) {
          // Degenerate cases: no pages left to merge, or just one.
          // End this pass.
          return Future.successful(nextPass ++ thisPassTodo)
        }

        val (thisOneTodo, rest) = thisPassTodo.splitAt(mergeFactor)

        if (rest.isEmpty && nextPass.size + thisOneTodo.size <= mergeFactor) {
          // the next pass is the final one, and this merge is unnecessary.
          return Future.successful(nextPass ++ thisOneTodo)
        }

        val recordSource = mergeAllPagesAtOnce(
          thisOneTodo,
          (n) => onProgress(n + nRecordsMergedBeforeCurrentMerge),
          callOnProgressEveryNRecords
        )

        PageOnDisk.create(tempDirectory, recordSource).flatMap { pageOnDisk =>
          nRecordsMergedBeforeCurrentMerge += pageOnDisk.nRecords
          mergeOneThenRecurse(rest, nextPass :+ pageOnDisk)
        }
      }

      mergeOneThenRecurse(thisPassPages, immutable.Seq()).flatMap { nextPassPages =>
        mergeOnePassThenRecurse(nextPassPages)
      }
    }

    mergeOnePassThenRecurse(pagesOnDisk)
  }

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

  private def waitForUnits[A,B](left: Future[A], right: Future[B])(implicit ec: ExecutionContext): Future[Unit] = {
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
  private[sort] def calculateNMerges(recordCounts: immutable.Seq[Int], mergeFactor: Int): Int = {
    assert(mergeFactor > 1) // or we'll get an infinite loop

    if (recordCounts.size == 0) return 0

    var ret: Int = 0
    var toMerge: immutable.Seq[Int] = recordCounts

    while (toMerge.size > mergeFactor) {
      val grouped = toMerge.grouped(mergeFactor).zipWithIndex.to[immutable.Seq]

      toMerge = grouped
        .flatMap { t: Tuple2[immutable.Seq[Int], Int] =>
          val nRecords = t._1.reduce(_ + _)

          val nPagesMergedBeforeThisOne = t._2
          if (nPagesMergedBeforeThisOne == grouped.size - 1) {
            // we're merging the last page. Maybe we can skip this merge?
            if (nPagesMergedBeforeThisOne + t._1.size <= mergeFactor) {
              t._1
            } else {
              ret += nRecords
              immutable.Seq(nRecords)
            }
          } else {
            ret += nRecords
            immutable.Seq(nRecords)
          }
        }
    }

    ret += toMerge.reduce(_ + _)

    ret
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
  def recordSourceToIdArray(recordSource: RecordSource)(implicit mat: Materializer, ec: ExecutionContext): Future[Array[Int]] = {
    val ret = new Array[Int](recordSource.nRecords)
    val sink = Sink.foreach { t: Tuple2[Int,Long] =>
      val id = t._1
      val index = t._2.toInt
      ret(index) = id
    }
    recordSource.records
      .map(_.id)
      .zipWithIndex
      .toMat(sink)(waitForUnits)
      .run
      .map(_ => ret)
  }
}
