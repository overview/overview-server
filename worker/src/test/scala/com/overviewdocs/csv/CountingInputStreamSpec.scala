package com.overviewdocs.csv

import com.overviewdocs.test.Specification
import org.specs2.specification.Scope
import java.io.ByteArrayInputStream

class CountingInputStreamSpec extends Specification {

  "CountingInputStream" should {

    trait InputStreamContext extends Scope {
      val size = 10
      def data: Array[Byte]
      val buffer = new Array[Byte](size)
      val rawStream = new ByteArrayInputStream(data)
      val countingStream = new CountingInputStream(rawStream)
    }

    trait FullStreamContext extends InputStreamContext {
      def data: Array[Byte] = Array.fill(100)(78)
    }

    trait EmptyStreamContext extends InputStreamContext {
      def data = Array[Byte]()
    }

    "handle single char read" in new FullStreamContext {
      countingStream.bytesRead must be equalTo (0)
      countingStream.read
      countingStream.bytesRead must be equalTo (1)
    }

    "handle read into buffer" in new FullStreamContext {
      countingStream.read(buffer) must be equalTo (size)
      countingStream.bytesRead must be equalTo (size)
    }

    "handle read into buffer with offset and len" in new FullStreamContext {
      val len = 5
      countingStream.read(buffer, 0, len) must be equalTo (len)
      countingStream.bytesRead must be equalTo (len)
    }

    "handle single char read past eos" in new EmptyStreamContext {
      countingStream.read
      countingStream.bytesRead must be equalTo (0)
    }

    "handle read into buffer past eos" in new EmptyStreamContext {
      countingStream.read(buffer) must be equalTo (-1)
      countingStream.bytesRead must be equalTo (0)
    }
  }
}
