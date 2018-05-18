package com.overviewdocs.searchindex

import java.nio.{ByteBuffer,CharBuffer}
import play.api.libs.json.{JsArray, JsNumber}

sealed trait Highlight {
  /** Ensures this is a Utf8Highlight -- that is, that offsets are bytes. */
  def toUtf8Highlight(text: String): Utf8Highlight;
}

/** A place in a document where a search query was found.
  *
  * The highlighted section is [begin,end) in UTF-16-encoded text.
  */
case class Utf16Highlight(begin: Int, end: Int) extends Highlight {
  override def toUtf8Highlight(text: String) = {
    assert(end <= text.length)
    assert(begin <= text.length)
    assert(begin <= end)
    assert(begin >= 0)

    val utf8 = java.nio.charset.StandardCharsets.UTF_8
    val encoder = utf8.newEncoder
    val byteBuffer = ByteBuffer.allocate(Seq(begin, end - begin).max * Math.ceil(encoder.maxBytesPerChar).toInt)

    val result1 = encoder.encode(CharBuffer.wrap(text.substring(0, begin)), byteBuffer, false)
    assert(!result1.isError)
    val utf8Begin = byteBuffer.position

    byteBuffer.position(0) // saves memory
    val result2 = encoder.encode(CharBuffer.wrap(text.substring(begin, end)), byteBuffer, true)
    assert(!result2.isError)
    val utf8Length = byteBuffer.position

    Utf8Highlight(utf8Begin, utf8Begin + utf8Length)
  }
}

/** A place in a document where a search query was found.
  *
  * The highlighted section is [begin,end) in UTF-8-encoded text.
  */
case class Utf8Highlight(begin: Int, end: Int) extends Highlight {
  override def toUtf8Highlight(text: String) = this
}
