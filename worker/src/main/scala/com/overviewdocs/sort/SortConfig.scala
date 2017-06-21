package com.overviewdocs.sort

import java.nio.file.Path

case class SortConfig(
  /** How much more costly the input read+write is than any Tempfile read+write.
    *
    * This is because a networked Database source -- and an in-memory sort -- is
    * far slower than reading and writing Tempfiles.
    *
    * This number is an estimate. The higher your guess, the faster the
    * progressbar will move: if you guess too high, the progressbar will "slow
    * down" once the first read+write is done.
    */
  firstReadCostBoost: Double = 5.0,

  /** How often to report progress during initial read. */
  firstReadNRecordsPerProgressCall: Int = 200,

  /** How often to report progress during merge. */
  mergeNRecordsPerProgressCall: Int = 2000,

  /** Where to store temporary files. */
  tempDirectory: Path,

  /** How much memory we can consume before writing to disk.
    *
    * A larger amount makes sorting faster, but it reduces the accuracy of
    * progress reporting. (Large in-memory sorts can take a while, and we don't
    * interrupt them to report progress).
    *
    * XXX sorting will sometimes exceed this amount momentarily. (When we coded
    * this we didn't need precision.) Bug reports+fixes welcome.
    */
  maxNBytesInMemory: Int,

  /** How many tempfiles to merge at a time.
    *
    * Higher numbers reduce the number of iterations, but they increase the
    * number of file handles and CPU instructions each iteration takes.
    *
    * In the `Sorter` documentation, this is `M`.
    */
  mergeFactor: Int = 16
)
