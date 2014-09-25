package models.archive.streamingzip

class Zip64CentralDirectory(entries: Iterable[Zip64LocalFileEntry]) {
  private val Zip64EndOfCentralDirectoryRecordSize = 56
  private val Zip64EndOfCentralDirectoryLocatorSize = 20
  private val EndOfCentralDirectoryRecordSize = 22

  private val localCentralFileHeaders: Seq[Zip64CentralFileHeader] = 
    entries.map(f => new Zip64CentralFileHeader(f.fileName, f.fileSize, 0, f.timeStamp, f.data)).toSeq
  
  def size: Long = localCentralFileHeaders.map(_.size).sum + 
  	Zip64EndOfCentralDirectoryRecordSize + Zip64EndOfCentralDirectoryLocatorSize + EndOfCentralDirectoryRecordSize 

}