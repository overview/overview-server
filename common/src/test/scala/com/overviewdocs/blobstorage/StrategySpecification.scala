package com.overviewdocs.blobstorage

import java.nio.file.{Files,Path}
import org.specs2.mock.Mockito
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import scala.collection.mutable

trait StrategySpecification
  extends Specification
  with StrategySpecHelper
  with Mockito
{
  sequential

  trait TempFileMethods {
    private val tempPaths = mutable.Buffer[Path]()

    protected def tempFile(contents: String): Path = {
      val path = Files.createTempFile("strategy-specification-", ".tmp")
      tempPaths.+=(path)
      Files.write(path, contents.getBytes("utf-8"))
      path
    }

    protected def unlinkTempFiles = {
      tempPaths.foreach(Files.deleteIfExists _)
    }
  }

  trait BaseScope extends Scope with TempFileMethods with After {
    override def after = unlinkTempFiles
  }
}
