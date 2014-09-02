/*
 * UploadReader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.{BufferedReader,InputStream,InputStreamReader,Reader}
import java.nio.charset.{Charset,CharsetDecoder,CodingErrorAction}
import java.sql.Connection
import scala.util.control.Exception.allCatch
import org.overviewproject.backports.sun.nio.cs.UTF_8
import org.overviewproject.database.{ Database, DB }
import org.overviewproject.persistence.EncodedUploadFile
import org.overviewproject.postgres.{ LO, LargeObjectInputStream }


/**
 * Provides a context for reading an uploaded file from the database. The
 * reader uses the CharsetDecoder specified by the uploaded file's encoding.
 */
class UploadReader() {
  private val Utf8 = new UTF_8()

  private var countingInputStream: CountingInputStream = _

  /** @return a reader for the given UploadedFile */
  def reader(contentsOid: Long, uploadedFile: EncodedUploadFile): Reader = {
    val fileDecoder = decoder(uploadedFile.encoding)
    val largeObjectInputStream = new LargeObjectInputStream(contentsOid)
    countingInputStream = new CountingInputStream(largeObjectInputStream)

    val inputStreamReader = new InputStreamReader(countingInputStream, fileDecoder)
    new BufferedReader(inputStreamReader)
  }

  /** @return the number of bytes read from the uploaded file */
  def bytesRead: Long =
    if (countingInputStream != null) countingInputStream.bytesRead
    else 0l


  /** The CharsetDecoder defined by encoding, if present and valid.
    *
    * By default, a UTF-8 decoder is used. Note: this is an _actual_ UTF-8
    * decoder, backported from a JDK9 snapshot; this is incompatible with
    * JDK7's UTF-8 decoder, which is actually a CESU-8 decoder.
    *
    * See https://bugs.openjdk.java.net/browse/JDK-7096080
    */
  private def decoder(encoding: Option[String]): CharsetDecoder = {
    val charset = (encoding match {
      case Some("utf-8") | Some("UTF-8") | None => None // default
      case Some(name) => allCatch.opt(Charset.forName(name))
    }).getOrElse(Utf8)

    charset.newDecoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE)
  }
}
