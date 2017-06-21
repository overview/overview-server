package com.overviewdocs.sort

import akka.util.ByteString
import java.nio.ByteBuffer
import java.util.Arrays

/** All the information we need to sort IDs.
  */
case class Record(
  /** ID to sort. */
  id: Int,

  /** The "default" position: where to position this ID if all data is null.
    *
    * We use this to tie-break when two IDs would ordinarily be sorted to the
    * same place.
    */
  canonicalPosition: Int,

  /** The data we compare while sorting.
    *
    * For instance, this could be the output of
    * `com.ibm.icu.text.CollationKey.toByteArray`.
    */
  collationKey: Array[Byte]
) extends Ordered[Record] {
  /** Amount of space this record consumes in memory.
    *
    * This follows guidelines from
    * https://github.com/jbellis/jamm/blob/master/src/org/github/jamm/MemoryLayoutSpecification.java
    * but without the dependency.
    */
  def nBytesEstimate: Int = {
    val nBytesSelfOverhead = 12
    val nBytesData = 4 + 4 + collationKey.length
    val nBytesArrayOverhead = 16
    nBytesSelfOverhead + nBytesArrayOverhead + nBytesData
  }

  override def compare(that: Record): Int = {
    val byBytes = ByteBuffer.wrap(this.collationKey).compareTo(ByteBuffer.wrap(that.collationKey))
    if (byBytes != 0) {
      byBytes
    } else {
      this.canonicalPosition - that.canonicalPosition
    }
  }

  override def equals(that: Any): Boolean = that match {
    case Record(thatId, thatCanonicalPosition, thatCollationKey) => {
      id == thatId && canonicalPosition == thatCanonicalPosition && Arrays.equals(collationKey, thatCollationKey)
    }
    case _ => false
  }

  override def toString: String = s"Record(${id},${canonicalPosition},${collationKey.mkString(" ")})"
}
