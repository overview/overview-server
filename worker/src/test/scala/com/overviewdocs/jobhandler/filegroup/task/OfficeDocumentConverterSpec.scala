package com.overviewdocs.jobhandler.filegroup.task

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files,Path}
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import scala.concurrent.duration.FiniteDuration

import com.overviewdocs.jobhandler.filegroup.TaskIdPool
import com.overviewdocs.util.{AwaitMethod,Configuration,Logger}

class OfficeDocumentConverterSpec extends Specification {
  import scala.concurrent.ExecutionContext.Implicits.global

  trait BaseScope extends Scope with After with AwaitMethod {
    class TestOfficeDocumentConverter(
      override val timeout: FiniteDuration = FiniteDuration(10, "s"),
      override val logger: Logger = Logger.forClass(getClass),
      override val taskIdPool: TaskIdPool = new TaskIdPool()
    ) extends OfficeDocumentConverter

    val tempDir: Path = Files.createTempDirectory("OfficeDocumentConverterSpec")
    val timeout = FiniteDuration(10, "s")

    def createFile(basename: String, contents: String): Path = {
      val path = tempDir.resolve(basename)
      Files.write(path, contents.getBytes(UTF_8))
      path
    }

    lazy val converter = new TestOfficeDocumentConverter(timeout=timeout)

    override def after = {
      val paths = Files.walk(tempDir).toArray(n => Array.fill[Path](n)(null)).toVector

      paths.reverse.foreach(Files.delete _)
    }
  }

  "OfficeDocumentConverter" should {
    "work on first invocation" in new BaseScope {
      val path = createFile("foo.txt", "foo")
      val newPath = await(converter.convertFileToPdf(path))
      val exists = Files.exists(newPath)
      Files.deleteIfExists(newPath)
      exists must beTrue
    }
  }
}
