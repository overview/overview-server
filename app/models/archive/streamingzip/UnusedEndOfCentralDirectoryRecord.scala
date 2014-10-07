package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream

/**
 *  End of Central Directory Record for a Zip64 formatted archive.
 *  Most values are actually found in the Zip64 End of Central Directory Record,
 *  so values here are unused.
 */
class UnusedEndOfCentralDirectoryRecord extends EndOfCentralDirectoryRecord(Seq.empty, Seq.empty) {

  override protected val centralDirectorySize = unused
  override protected val centralDirectoryOffset = unused

  override protected val numberOfEntries = unused

}