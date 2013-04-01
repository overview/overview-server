/**
 * TempFile.scala
 * This class spools data to a temp file disk, and providers a reader to read it back. 
 * The file is deleted when the class is destroyed (or process quits)
 * 
 * @author JonathanStray
 * created March 2013
 */

package org.overviewproject.util

import java.io.{Writer, File, FileReader, FileWriter, IOException}

// Temp file will exist until both TempFile.close() and TempFile.reader.close() are called
// or when object is GC'd
class TempFile extends java.io.Writer {

  private val file = java.io.File.createTempFile("overview-", "-docset.csv")
  
  // Open the file for reading and then unreference, so OS will delete for sure when file closes -- at worst, when process exits
  private val _writer = new FileWriter(file)
  private val _reader = new FileReader(file)  
  file.delete()

  private var closed = false
  
  // Make us act like a writer, by forwarding
  def write(str:Array[Char], off:Int, len:Int) = _writer.write(str,off,len)
  def flush() = _writer.flush()
  def close() = _writer.close()   // cannot write after this; getReader.close (or GC) will then remove the file
  
  // We have only one reader
  def reader:java.io.Reader = _reader
  
}