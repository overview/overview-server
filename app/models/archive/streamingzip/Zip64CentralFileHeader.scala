package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import java.util.zip.CheckedInputStream
import models.archive.DosDate

/**
 * Central Directory File Headers in Zip64 format
 */
class Zip64CentralFileHeader(fileName: String, fileSize: Long, offset: Long, timeStamp: DosDate, crcFunction: => Int)
    extends CentralFileHeader(fileName, fileSize, offset, timeStamp, crcFunction) {

  override protected val extractorVersion: Short = useZip64Format
  override protected val compressedSize = unused
  override protected val uncompressedSize = unused
  override protected val extraFieldLength = centralDirectoryExtraField
  private val extraFieldDataSize = centralDirectoryExtraField - 4

  override protected val extraField =
    writeShort(zip64ExtraFieldTag) ++
      writeShort(extraFieldDataSize.toShort) ++
      writeLong(fileSize) ++
      writeLong(fileSize) ++
      writeLong(offset)

  override protected val localHeaderOffset: Long = unused

}