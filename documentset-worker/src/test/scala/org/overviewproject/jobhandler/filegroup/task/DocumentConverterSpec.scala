package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import java.io.InputStream
import java.util.UUID
import java.io.File
import java.nio.file.Paths
import org.specs2.specification.Scope

class DocumentConverterSpec extends Specification with Mockito {

  "DocumentConverter" should {

    "call conversion command with the correct parameters" in new DocumentConverterContext {
      documentConverter.convertToPdfStream(guid, inputStream)(identity)

      there was one(documentConverter.runner).run(libreOfficeCommand)
    }

    "write the input stream to a temp file which is deleted after conversion" in new DocumentConverterContext {

      documentConverter.convertToPdfStream(guid, inputStream)(identity)

      there was one(documentConverter.fileSystem).saveToFile(inputStream, inputFilePath)
      there was one(documentConverter.fileSystem).deleteFile(inputFile)
    }

    "delete output file after conversion is complete" in new DocumentConverterContext {
      documentConverter.convertToPdfStream(guid, inputStream)(identity)
      
      there was one(documentConverter.fileSystem).deleteFile(outputFile)
    }

    "call processing function with converted output stream" in new DocumentConverterContext {
      val result =  documentConverter.convertToPdfStream(guid, inputStream)(identity)
      
      result must be equalTo(documentConverter.convertedStream)
    }

    "delete input file if exception is thrown during processing" in {
      todo
    }

    "throw a ConverterFailedException if office command returns an error" in {
      todo
    }

    "delete output file if exception is thrown during processing" in {
      todo
    }

    "throw a NoConverterOutput if no output file is found" in {
      todo
    }

  }

  trait DocumentConverterContext extends Scope {
    def tempDir = {
      val tmpDir = System.getProperty("java.io.tmpdir")
      Paths.get(tmpDir, "overview-documentset-worker")
    }
    val fileGuid = "ccddeeff-1122-3344-5566-77889900aabb"
    val outputFileName = s"$fileGuid.pdf"
    val inputFilePath = tempDir.resolve(Paths.get(fileGuid))
    val inputFile = inputFilePath.toFile
    val outputFile = tempDir.resolve(Paths.get(outputFileName)).toFile

    val officeCommandPieces = Seq(
      "/Applications/LibreOffice.app/Contents/MacOS/soffice",
      "--headless --invisible --norestore --nolockcheck --convert-to pdf",
      s"--outdir $tempDir $inputFilePath")
    val libreOfficeCommand = officeCommandPieces.mkString(" ")

    val guid = UUID.fromString(fileGuid)
    val inputStream = mock[InputStream]
    val documentConverter = new TestDocumentConverter(inputFile)

  }

  class TestDocumentConverter(inputFile: File) extends DocumentConverter {
    override val runner = mock[Runner]
    override val fileSystem = mock[FileSystem]

    val convertedStream = mock[InputStream]

    runner.run(any) returns Right("Success")
    fileSystem.saveToFile(any, any) returns inputFile
    fileSystem.readFile(any) returns convertedStream
  }
}