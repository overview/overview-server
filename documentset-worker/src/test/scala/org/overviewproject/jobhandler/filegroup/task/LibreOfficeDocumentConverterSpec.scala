package org.overviewproject.jobhandler.filegroup.task

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Paths
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.util.control.Exception._
import org.overviewproject.jobhandler.filegroup.task.LibreOfficeDocumentConverter._
import org.overviewproject.util.Configuration
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class LibreOfficeDocumentConverterSpec extends Specification with Mockito with NoTimeConversions {
  "LibreOfficeDocumentConverter" should {
    "call conversion command with the correct parameters" in new BaseScope {
      documentConverter.withStreamAsPdf(guid, inputStream)(doNothing _)

      there was one(documentConverter.runner).run(libreOfficeCommand, documentConverter.timeout)
    }

    "write the input stream to a temp file which is deleted after conversion" in new BaseScope {

      convertAndCall(doNothing _)

      there was one(documentConverter.fileSystem).saveToFile(inputStream, inputFilePath)
      there was one(documentConverter.fileSystem).deleteFile(inputFile)
    }

    "delete output file after conversion is complete" in new BaseScope {
      convertAndCall(doNothing _)
      
      there was one(documentConverter.fileSystem).deleteFile(outputFile)
    }

    "call processing function with converted output stream" in new BaseScope {
      val result = convertAndCall(collectParams)

      result must be_==((documentConverter.convertedStream, documentConverter.convertedSize))
    }

    "delete input file if exception is thrown during processing" in new BaseScope {
      ignoring(classOf[Exception]) { convertAndCall(failingProcessing) }

      there was one(documentConverter.fileSystem).deleteFile(inputFile)
    }

    "return failed future if office command returns an error" in new BaseScope {
      val failingConverter = new FailingConverter(inputFile)
      val r = failingConverter.withStreamAsPdf(guid, inputStream)(doNothing _) 
      Await.result(r, Duration.Inf) must throwA[Exception]
    }

    "delete output file if exception is thrown during processing" in new BaseScope {
      ignoring(classOf[Exception]) { convertAndCall(failingProcessing) }

      there was one(documentConverter.fileSystem).deleteFile(outputFile)
    }

    "throw a NoConverterOutput if no output file is found" in new BaseScope {
      val noOutputConverter = new ConverterWithNoOutput(inputFile)

     val r = noOutputConverter.withStreamAsPdf(guid, inputStream)(doNothing _)
      Await.result(r, Duration.Inf) must throwA[LibreOfficeNoOutputException]
    }

  }

  trait BaseScope extends Scope {
    def convertAndCall[T](f: (InputStream, Long) => Future[T]): T = 
      Await.result(documentConverter.withStreamAsPdf(guid, inputStream)(f), Duration.Inf)
      
    def tempDir = {
      val tmpDir = System.getProperty("java.io.tmpdir")
      Paths.get(tmpDir, "overview-documentset-worker")
    }
    val fileGuid = "ccddeeff-1122-3344-5566-77889900aabb"
    val filename = "abcd.doc"
    val outputFileName = s"$fileGuid.pdf"
    val inputFilePath = tempDir.resolve(Paths.get(fileGuid))
    val inputFile = inputFilePath.toFile
    val outputFile = tempDir.resolve(Paths.get(outputFileName)).toFile
    
    val officeCommandPieces = Seq(
      Configuration.getString("libre_office_path"),
      "--headless --nologo --invisible --norestore --nolockcheck --convert-to pdf",
      s"--outdir $tempDir $inputFilePath")
    val libreOfficeCommand = officeCommandPieces.mkString(" ")

    val guid = UUID.fromString(fileGuid)
    val inputStream = mock[InputStream]
    val documentConverter = new TestLibreOfficeDocumentConverter(inputFile)

    def doNothing(inputStream: InputStream, size: Long): Future[Unit] = Future.successful(())
    
    def collectParams(inputStream: InputStream, size: Long): Future[(InputStream, Long)] = 
      Future.successful((inputStream, size))
          
    def failingProcessing(inputStream: InputStream, size: Long): Future[Unit] = throw new Exception("something went wrong")

  }

  class TestLibreOfficeDocumentConverter(inputFile: File) extends LibreOfficeDocumentConverter {
    override val runner = smartMock[ShellRunner]
    override val fileSystem = smartMock[FileSystem]
    override protected val conversionTimeout = 1 millis
    override implicit protected val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
    
    val convertedStream = mock[InputStream]
    val convertedSize = 4L

    runner.run(any, any) returns runResult
    fileSystem.saveToFile(any, any) returns inputFile

    setupReadFile

    def setupReadFile: Unit = {
      fileSystem.readFile(any) returns convertedStream
      fileSystem.getFileLength(any) returns convertedSize
    }

    def runResult: Future[String] = Future.successful("Success")
    def readFileResult: InputStream = convertedStream
    def timeout = conversionTimeout
  }

  class FailingConverter(inputFile: File) extends TestLibreOfficeDocumentConverter(inputFile) {
    override def runResult: Future[String] = Future.failed(new RuntimeException)
  }

  class ConverterWithNoOutput(inputFile: File) extends TestLibreOfficeDocumentConverter(inputFile) {
     override def setupReadFile: Unit = fileSystem.readFile(any) answers {_ => throw new FileNotFoundException } 
  }
}
