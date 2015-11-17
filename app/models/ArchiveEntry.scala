package models

/** The minimum amount of information possible to represent a file for
  * archiving.
  */
case class ArchiveEntry(
  /** Document ID.
    *
    * Necessary because: we need to know how to fetch the file's contents. The
    * document ID is the smallest amount of data we can store to accomplish that
    * goal.
    */
  documentId: Long,

  /** Filename, including path and extension.
    *
    * Necessary because: we need to know the number of bytes in each filename
    * to determine the archive's total size. But we don't need to decode/encode
    * the UTF-8 bytes: the database stores them as UTF-8, and the zipfile will
    * store them as UTF-8.
    */
  filenameUtf8: Array[Byte],

  /** File size, in bytes.
    *
    * Necessary because: we need to know the number of bytes in each file to
    * determine the archive's total size.
    */
  nBytes: Long
) {
  override def equals(that: Any) = that match {
    case ArchiveEntry(aDocumentId, aFilenameUtf8, aNBytes) => {
      documentId == aDocumentId && filenameUtf8.sameElements(aFilenameUtf8) && nBytes == aNBytes
    }
    case _ => false
  }

  override def toString = s"ArchiveEntry($documentId,${new String(filenameUtf8, "utf-8")},$nBytes)"
}

object ArchiveEntry {
  private val Period = 0x2e.toByte

  /** Table mapping input bytes to corresponding output bytes.
    *
    * This is mostly pass-through. Multi-byte UTF-8 characters are unaffected.
    */
  private val ValidBytes: Array[Byte] = {
    val invalid: Seq[Byte] = Seq.range(0, 0x20).map(_.toByte) ++ "?%*:|\"<>".toSeq.map(_.toByte)

    val ret = Array.tabulate(0xff)(_.toByte)
    invalid.foreach { b => ret(b) = '_'.toByte }
    ret('\\'.toByte) = '/'.toByte

    ret
  }

  /** A copy of the input Array, ending in the given extension.
    *
    * For instance:
    *
    *     val filename = "foo.txt".getBytes("utf-8")
    *     val filenamePdf = ArchiveEntry.ensureUtf8FilenameHasExtension(filename, ".pdf".getBytes("utf-8"))
    *     new String(filenamePdf, "utf-8") // "foo.pdf"
    *
    * Logic:
    *
    * * Replaces "\.[^\.]{1,4}" with `extension`
    * * Replaces disallowed characters with sensible equivalents
    * * Replaces `\` with `/`
    */
  def sanitizeFilenameUtf8WithExtension(filename: Array[Byte], extension: Array[Byte]): Array[Byte] = {
    val index = filename.lastIndexOf(Period)
    val nBytesToCopy = if (index > -1 && filename.length - index < 6) index else filename.length

    val ret = new Array[Byte](nBytesToCopy + extension.length)

    for (i <- 0 until nBytesToCopy) {
      ret(i) = ValidBytes(filename(i) & 0xff)
    }
    extension.copyToArray(ret, nBytesToCopy)

    ret
  }
}
