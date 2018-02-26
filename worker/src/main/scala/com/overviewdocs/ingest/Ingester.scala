package com.overviewdocs.ingest

import akka.stream.scaladsl.{Flow,Source}
import scala.collection.{immutable,mutable}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.models.{ProcessedFile2,IngestedRootFile2}

object Ingester {
  /** Converts Processed File2s into Ingested ones by creating Documents and
    * DocumentProcessingErrors.
    *
    * Any INGESTED File2 has had all its Documents and/or File2Errors written.
    * Ingesting an INGESTED File2 is a no-op.
    *
    * Ingesting can't be canceled. (Compared to processing, it's super-quick.
    * Users can wait the few seconds.)
    *
    * This method is meant to be called with an infinite input stream, producing
    * an infinite output stream. Input and output orderings are not defined, but
    * this class enforces an _internal_ rule that database writes must happen on
    * children before parents, so a crash doesn't leave ingested parents with
    * non-ingested children.
    */
  def ingest(
    file2Writer: File2Writer,
    batchSize: Int = 500,
    maxBatchWait: FiniteDuration = FiniteDuration(100, "ms")
  )(implicit ec: ExecutionContext): Flow[ProcessedFile2, IngestedRootFile2, akka.NotUsed] = {
    // "holding": parents whose children aren't written yet.
    //
    // Whenever there are no ongoing conversions or writes, this should be
    // empty. It is designed so that even during busy conversion, it only
    // consumes ~100 bytes per un-ingested parent file.
    //
    // This variable isn't mutable, and we never mutate it. We just use
    // the mutable.LongMap implementation.
    var unwrittenParents: mutable.LongMap[ProcessedFile2] = mutable.LongMap.empty

    // From a batch of processed files, returns (unwrittenParents, toWrite).
    //
    // Assumes it's called on parents before children ... but both parents and
    // children can appear in the same input batch. The return value can include
    // both parents and their children in the same batch, too, in _reverse_:
    // children before parents.
    //
    // unwrittenParents is the state: pass it in the next call to fixHierarchy.
    def fixHierarchy(
      unwrittenParents: mutable.LongMap[ProcessedFile2],
      file2s: immutable.Seq[ProcessedFile2]
    ): (mutable.LongMap[ProcessedFile2], immutable.Seq[ProcessedFile2]) = {
      // A pool of ProcessedFile2s. invariant: areChildrenIngested == false
      val holding = unwrittenParents.clone

      // Focus ProcessedFile2s: we need to look at them to learn whether to
      // write or hold them.
      var staging: immutable.Seq[ProcessedFile2] = file2s

      // ProcessedFile2s with invariant: areChildrenIngested == true
      //
      // The children may be ingested within this very batch.
      val writing: mutable.ArrayBuffer[immutable.Seq[ProcessedFile2]] = mutable.ArrayBuffer.empty

      while (staging.nonEmpty) {
        // Move all entries from "staging" to either "writing" or "holding".
        val (toWrite, toHold) = staging.partition(_.areChildrenIngested)
        writing.+=(toWrite)
        holding.++=(toHold.map(f => (f.id -> f)))

        // Count the children we committed to writing this step
        val focusParentCounts: Map[Long,Int] = toWrite
          .flatMap(_.parentId)
          .groupBy(identity)
          .mapValues(_.size) // parentId => nChildrenWrittenThisStep

        // For children we wrote, remove all parents from "holding", adjust
        // their counts, and store under "staging".
        //
        // "staging" will become empty iff "toWrite" is empty or only contains
        // roots.
        staging = focusParentCounts
          .map((kv: Tuple2[Long,Int]) => holding.remove(kv._1) match {
            case None => throw new RuntimeException(s"Source emitted a child of parent ${kv._1} before the parent itself")
            case Some(parent) => parent.copy(nIngestedChildren = parent.nIngestedChildren + kv._2)
          })
          .toVector
      }

      holding.repack
      (holding, writing.flatten.toVector)
    }

    def ingestBatch(
      file2s: immutable.Seq[ProcessedFile2]
    )(implicit ec: ExecutionContext): Future[immutable.Seq[IngestedRootFile2]] = {
      // This is effectively synchronous: it's only called once at a time,
      // always from the same thread, waiting for the returned Future to
      // complete before calling again. (akka-streams ftw.)
      val (toHold, toWrite) = fixHierarchy(unwrittenParents, file2s)

      unwrittenParents = toHold // for next time

      for {
        _ <- if (toWrite.nonEmpty) { file2Writer.ingestBatch(toWrite) } else { Future.unit }
      } yield {
        val roots = toWrite.filter(_.parentId.isEmpty)
        roots.map(root => IngestedRootFile2(root.id, root.documentSetId))
      }
    }

    Flow[ProcessedFile2]
      .groupedWithin(batchSize, maxBatchWait)
      .mapAsync(1)(ingestBatch _)
      .mapConcat(identity)
  }
}
