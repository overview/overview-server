package util

import org.specs2.mutable.Specification

class BitSetUtilSpec extends Specification {
  "BitSetUtil" should {
    "bytesToBitSet" should {
      "get a BitSet" in {
        // 1, 3, 5, 11:
        // 0b0101010000010... in a bitset
        BitSetUtil.bytesToBitSet(Array[Byte](0x54, 0x10)).toVector must beEqualTo(Vector(1, 3, 5, 11))
      }

      "read numbers >63 (that is, multi-word bitsets)" in {
        // 2, 66, 67:
        // 0b00100000...110000
        // byte 1: 0b00100000
        // bytes 2-8: empty
        // byte 9: 0b11000000
        BitSetUtil.bytesToBitSet(Array[Byte](0x20, 0, 0, 0, 0, 0, 0, 0, 0xc0.toByte)).toVector must beEqualTo(Vector(2, 64, 65))
      }

      "allow empty Array as empty bitset" in {
        BitSetUtil.bytesToBitSet(Array[Byte]()).toVector must beEmpty
      }
    }
  }
}
