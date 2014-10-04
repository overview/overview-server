package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.nio.charset.StandardCharsets

class LocalFileEntry(fileName: String, fileSize: Long, data: InputStream) extends LittleEndianWriter with ZipFormat {

  protected val extractorVersion = defaultVersion
  protected val flags = useDataDescriptor | useUTF8
  protected val timeStamp = DosDate(Calendar.getInstance())
  protected val fileNameLength = fileName.getBytes(StandardCharsets.UTF_8).size.toShort
  
  val stream: InputStream = headerStream
    
  private def headerStream: InputStream =
    new ByteArrayInputStream(
      writeInt(localFileEntrySignature) ++
        writeShort(extractorVersion) ++
        writeShort(flags) ++
        writeShort(noCompression) ++
        writeShort(timeStamp.time.toShort) ++
        writeShort(timeStamp.date.toShort) ++
        writeInt(empty) ++
        writeInt(empty) ++
        writeInt(empty) ++
        writeShort(fileNameLength) ++
        writeShort(empty))

}