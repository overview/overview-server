package com.overviewdocs.blobstorage

import java.io.ByteArrayInputStream
import play.api.libs.iteratee.{ Enumerator, Iteratee }

import com.overviewdocs.util.AwaitMethod

trait StrategySpecHelper extends AwaitMethod {
  def byteArrayInputStream(bytes: Array[Byte]) = new ByteArrayInputStream(bytes)
  def utf8InputStream(string: String) = byteArrayInputStream(string.getBytes("utf-8"))
  def consume(enumerator: Enumerator[Array[Byte]]): Array[Byte] = {
    val f = Iteratee.consume[Array[Byte]]()
    await(Iteratee.flatten(enumerator(f)).run)
  }

}
