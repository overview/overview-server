package com.overviewdocs.jobhandler.filegroup.task

import java.nio.file.Path
import java.nio.file.Paths
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DirectoryCleanerSpec extends Specification with Mockito {

  trait DirectoryCleanerContext extends Scope {
    class TestDirectoryCleaner extends DirectoryCleaner {
      val mockFileSystem = smartMock[FileSystem]

      override val fileSystem = mockFileSystem

      def initFileSystem(directoryContents: Map[Path, Seq[Path]]) = {
        directoryContents.foreach { case (dir, contents) =>
          mockFileSystem.listContents(dir) returns contents
          mockFileSystem.isDirectory(dir) returns true
        }
      }
    }

    val directoryCleaner = new TestDirectoryCleaner
  }

  "DirectorCleaner" should {

    "deletes the directory, then creates it" in new DirectoryCleanerContext {
      val directory = Paths.get("dirname")
      directoryCleaner.initFileSystem(Map((directory -> Seq())))
      directoryCleaner.createCleanDirectory(directory)

      there was one(directoryCleaner.mockFileSystem).createDirectory(directory)
      there was one(directoryCleaner.mockFileSystem).deleteIfExists(directory)
    }

    "deletes directory contents recursively" in new DirectoryCleanerContext {
      val directory = Paths.get("dirname")
      val file = Paths.get("file")
      val subdir = Paths.get("subdir")
      val subdirFile = Paths.get("file in subdir")

      directoryCleaner.initFileSystem(Map(
        (directory -> Seq(file, subdir)),
        (subdir -> Seq(subdirFile))
      ))

      directoryCleaner.createCleanDirectory(directory)

      there was one(directoryCleaner.mockFileSystem).deleteIfExists(file)
      there was one(directoryCleaner.mockFileSystem).deleteIfExists(subdirFile) andThen
        one(directoryCleaner.mockFileSystem).deleteIfExists(subdir) andThen
        one(directoryCleaner.mockFileSystem).deleteIfExists(directory)
    }
    
    "doesn't delete non-existing directory" in new DirectoryCleanerContext {
      val directory = Paths.get("dirname")
      directoryCleaner.createCleanDirectory(directory) must not(throwA[Exception])
    }

  }
}
