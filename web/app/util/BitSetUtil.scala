package util

import java.nio.ByteBuffer
import scala.collection.immutable.BitSet

object BitSetUtil {
  def bytesToBitSet(bytes: Array[Byte]): BitSet = {
    val byteBuffer = ByteBuffer
      .allocate((7 + bytes.length) / 8 * 8) // Pad it so .asLongBuffer works
    val longBuffer = byteBuffer.asLongBuffer
    byteBuffer.put(bytes)
    val longs = Array.ofDim[Long](longBuffer.capacity)
    longBuffer.get(longs)

    // Scala's BitSet stores the least-significant integer at the last
    // bit. For instance, if BitSet's internal Long is 0b0000...00011,
    // that stores the numbers 1 and 2. But over the wire, "1 and 2" is
    // sent as 0b11000000. So we need to reverse each Long -- but not
    // the order of the Longs themselves.
    val reversedLongs = longs.map(i => java.lang.Long.reverse(i))
    BitSet.fromBitMaskNoCopy(reversedLongs)
  }
}
