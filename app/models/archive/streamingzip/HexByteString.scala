package models.archive.streamingzip

import scala.util.control.Exception._

/**
 * Helper to make creating byte arrays simpler.
 * Call `"abcd01".hex` to get an `Array[Byte](0xab, 0xcd, 0x01)`
 * Portions of the string that can't be interpreted as hex bytes are ignored
 */
object HexByteString {
  import scala.language.implicitConversions

  class HexString(val s: String) {
    def hex: Array[Byte] = {
      val bytes = s.grouped(2)

      bytes.map(hexToByte).flatten.toArray
    }

    private def hexToByte(s: String): Option[Byte] = allCatch.opt {
      Integer.parseInt(s, 16).toByte
    }
  }

  implicit def string2hexBytes(s: String): HexString = new HexString(s)
}