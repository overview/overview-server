package models.archive.streamingzip

import java.nio.ByteBuffer
import java.nio.ByteOrder._

trait LittleEndianWriter {

  
  // Utilities to write values in little-endian order
  def writeLong(value: Long): Array[Byte] = 
    byteBuffer(8).putLong(value).array()
  
  
  def writeInt(value: Int): Array[Byte] = 
    byteBuffer(4).putInt(value).array()


  def writeShort(value: Int): Array[Byte] = 
    byteBuffer(2).putShort(value.toShort).array()

  
  private def byteBuffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(LITTLE_ENDIAN)


}