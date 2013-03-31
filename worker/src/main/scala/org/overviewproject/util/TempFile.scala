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

class TempFile extends java.io.Writer {

  private val file = java.io.File.createTempFile("overview-", "-docset.csv")
  
  // Open the file for reading and then unreference, so OS will delete for sure when file closes -- at worst, when process exits
  private val reader = new FileReader(file)  
  private val out = new FileWriter(file)
  file.delete()

  private var closed = false

  // Make us act like a writer, by forwarding
  def write(str:Array[Char], off:Int, len:Int) = out.write(str,off,len)
  def flush() = out.flush()

  // We have only one reader
  def getReader = {
    if (closed) 
      throw new java.io.IOException("Tried to read from a closed temp file")
    reader
  }

  // Close wraps everything up, deleting the file. Thereafter write, flush, reader will fail
  def close() = {
   closed = true
   out.close()
   reader.close()
  }  
}