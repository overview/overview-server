/**
 * TempFile.scala
 * This class spools data to a temp file disk, and providers a reader to read it back. 
 * The file is deleted when the class is destroyed (or process quits)
 * 
 * @author JonathanStray
 * created March 2013
 */

package org.overviewproject.util

import java.io._

/** Opens a reader and writer to a tempfile that has no filename.
  *
  * Upon construction, java.io.File.createTempFile() will be called, a
  * java.io.FileInputStream and java.io.FileOutputStream will be opened on the
  * result, and the file will be deleted. The operating system will
  * garbage-collect the tempfile when all references to it are deleted--that is,
  * when the streams are closed.
  *
  * To reclaim the disk space, do one of the following:
  *
  * 1. Call inputStream.close and outputStream.close
  * 2. Let the TempFile fall out of scope, so the JVM closes the streams; or
  * 3. End the JVM unexpectedly, so the operating system cleans things up
  *
  * Expected usage:
  *
  *   val tempfile = new TempFile
  *   tempfile.outputStream.write("Hello!".toByteArray)
  *   tempfile.outputStream.close
  *   tempfile.inputStream.read // returns 'H'
  *   tempfile.inputStream.close // reclaims disk space
  */
class TempFile {
  private def createWriterAndReaderStreams : (FileOutputStream, FileInputStream) = {
    val file = java.io.File.createTempFile("overview-", ".tmp")
     
    try {
      (new FileOutputStream(file), new FileInputStream(file))
    } finally {
      file.delete()
    }

    // Uncomment if you want to look at the generated tempfile
    //(new FileOutputStream("/tmp/overview-tmp"), new FileInputStream("/tmp/overview-tmp"))
  }

  val (outputStream : FileOutputStream, inputStream : FileInputStream) = createWriterAndReaderStreams
}
