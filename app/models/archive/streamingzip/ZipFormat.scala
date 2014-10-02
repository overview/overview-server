package models.archive.streamingzip

trait ZipFormat {
   val localFileEntrySignature: Int =  0x04034b50
   val dataDescriptorSignature: Int = 0x08074b50
   
   val useZip64Format: Short = 45
   val noCompression: Short = 0
   
   val useDataDescriptor: Short = 0x0008
   val useUTF8: Short = 0x0800
   
   val zip64ExtraFieldTag = 1
   
   val unused = -1
   val empty = 0
}