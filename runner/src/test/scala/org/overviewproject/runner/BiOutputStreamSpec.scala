package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,OutputStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito

class BiOutputStreamSpec extends Specification with Mockito {
  trait Base extends Scope {
    val outStream1 = new ByteArrayOutputStream()
    val outStream2 = new ByteArrayOutputStream()
    val out = new BiOutputStream(outStream1, outStream2)

    def bytes1 = outStream1.toByteArray()
    def bytes2 = outStream2.toByteArray()
  }

  trait Mocked extends Scope {
    val outStream1 = mock[OutputStream]
    val outStream2 = mock[OutputStream]
    val out = new BiOutputStream(outStream1, outStream2)
  }

  "BiOutputStream" should {
    "write(Byte) to both streams" in new Base {
      out.write(70)
      bytes1 must beEqualTo(Array[Byte](70.toByte))
      bytes2 must beEqualTo(Array[Byte](70.toByte))
    }

    "write(Array[Byte]) to both streams" in new Base {
      val bytes = Array[Byte](70.toByte, 71.toByte)
      out.write(bytes)
      bytes1 must beEqualTo(bytes)
      bytes2 must beEqualTo(bytes)
    }

    "write(Array[Byte],Int,Int) to both streams" in new Base {
      val bytes = Array[Byte](70.toByte, 71.toByte, 72.toByte, 73.toByte)
      val rBytes = bytes.slice(1,3)
      out.write(bytes, 1, 2)
      bytes1 must beEqualTo(rBytes)
      bytes2 must beEqualTo(rBytes)
    }

    "flush both streams" in new Mocked {
      out.flush()
      there was one(outStream1).flush()
      there was one(outStream2).flush()
    }

    "close both streams" in new Mocked {
      out.close()
      there was one(outStream1).close()
      there was one(outStream2).close()
    }
  }
}
