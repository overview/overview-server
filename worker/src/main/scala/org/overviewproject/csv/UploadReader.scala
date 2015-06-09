/*
 * UploadReader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.{BufferedInputStream,BufferedReader,InputStreamReader,Reader}
import java.nio.charset.{Charset,CharsetDecoder,CodingErrorAction}
import java.sql.Connection
import scala.util.control.Exception.allCatch

import org.overviewproject.backports.sun.nio.cs.UTF_8
import org.overviewproject.database.BlockingDatabase
import org.overviewproject.postgres.LargeObjectInputStream

/**
 * Provides a context for reading an uploaded file from the database. The
 * reader uses the CharsetDecoder specified by the uploaded file's encoding.
 */
class UploadReader(oid: Long, encodingStringOption: Option[String], blockingDatabase: BlockingDatabase) {
  // Make buffer small enough that progress moves swiftly but large enough
  // that we're not constantly opening connections to the database
  private val LargeObjectBufferSize = 5 * 1024 * 1024 // 5MB

  val charset = UploadReader.getCharset(encodingStringOption)

  private val decoder: CharsetDecoder = charset.newDecoder()
    .onMalformedInput(CodingErrorAction.REPLACE)
    .onUnmappableCharacter(CodingErrorAction.REPLACE)

  // Raw input
  private val largeObjectInputStream = new LargeObjectInputStream(oid, blockingDatabase)

  // Provide a means of reporting progress
  private val countingInputStream = new CountingInputStream(largeObjectInputStream)

  // Reduce the number of calls to LargeObjectInputStream.read()
  private val bufferedInputStream = new BufferedInputStream(countingInputStream, LargeObjectBufferSize)

  // Decode the text
  private val inputStreamReader = new InputStreamReader(bufferedInputStream, decoder)

  // Reduce the number of calls to the Reader
  // Use the Java-default buffer size, since Oracle knows better than we do
  private val bufferedReader = new BufferedReader(inputStreamReader)

  /** Reads the text from the database. */
  val reader: Reader = bufferedReader

  /** Returns the number of bytes read from the database.
    *
    * Note: this is not the number of bytes returned by the reader. (We don't
    * calculate that, because it would take too long.)
    */
  def bytesRead: Long = countingInputStream.bytesRead
}

object UploadReader {
  private val Utf8 = new UTF_8()

  /** The Charset defined by encoding, if present and valid.
    *
    * By default, UTF-8 is used. Note: this is an _actual_ UTF-8
    * decoder, backported from a JDK9 snapshot; this is incompatible with
    * JDK7's UTF-8 decoder, which is actually a CESU-8 decoder.
    *
    * See https://bugs.openjdk.java.net/browse/JDK-7096080
    */
  private def getCharset(encodingStringOption: Option[String]): Charset = {
    (encodingStringOption match {
      case Some("utf-8") | Some("UTF-8") | None => None // default
      case Some(name) => allCatch.opt(Charset.forName(name))
    }).getOrElse(Utf8)
  }
}
