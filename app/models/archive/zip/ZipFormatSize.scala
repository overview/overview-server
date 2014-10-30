package models.archive.zip

trait ZipFormatSize {
  val localFileHeader = 30
  val localFileHeaderExtraField = 20
  val dataDescriptor = 24
  val centralDirectoryHeader = 46
  val centralDirectoryExtraField = 28
  val zip64EndOfCentralDirectory= 56
  val zip64EndOfCentralDirectoryLocator = 20
  val endOfCentralDirectory = 22
}