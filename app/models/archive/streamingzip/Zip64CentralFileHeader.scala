package models.archive.streamingzip

import java.nio.charset.StandardCharsets

class Zip64CentralFileHeader(fileName: String) {
  private val DataSize = 46
  private val ExtraFieldSize = 32
  private val fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).length
  
  def size: Int = DataSize + ExtraFieldSize + fileNameSize
  

}