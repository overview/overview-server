package models.archive.streamingzip

trait ZipFormat {
   val localFileEntrySignature: Int =  0x04034b50
   val dataDescriptorSignature: Int = 0x08074b50
   val centralFileHeaderSignature: Int = 0x02014b50
   val zip64EndOfCentralDirectorySignature: Int = 0x06064b50
   val zip64EndOfCentralDirectoryLocatorSignature: Int = 0x07064b50
   val endOfCentralDirectoryRecordSignature: Int = 0x06054b50
   
   /** Version needed to Extract */
   val defaultVersion: Short = 10
   val useZip64Format: Short = 45
   
   /** Version made by */
   val unix: Short = 0x0300
   val zipSpecification: Short = 63  // 6.3.3 actually
   
   val noCompression: Short = 0
   
   /** flags */
   val useDataDescriptor: Short = 0x0008
   val useUTF8: Short = 0x0800
   
   val zip64ExtraFieldTag = 1
   
   val unused = -1
   val empty = 0
   
   val diskNumber = 0
   val totalNumberOfDisks = 1
}