package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import java.nio.file.Paths
import org.specs2.specification.Scope
import java.nio.file.Path
import org.specs2.mutable.Before

class DirectoryCleanerSpec extends Specification with Mockito {

  "DirectorCleaner" should {

    "deletes the directory, then creates it" in new DirectoryCleanerContext {
      directoryCleaner.createCleanDirectory(directory)

      there was one(directoryCleaner.fileSystem).createDirectory(directory)
      there was one(directoryCleaner.fileSystem).deleteIfExists(directory)
    }

    "deletes directory contents recursively" in new DirectoryWithContent {
      directoryCleaner.createCleanDirectory(directory)

      there was one(directoryCleaner.fileSystem).deleteIfExists(file)
      there was one(directoryCleaner.fileSystem).deleteIfExists(subdirFile) andThen
        one(directoryCleaner.fileSystem).deleteIfExists(subdir) andThen
        one(directoryCleaner.fileSystem).deleteIfExists(directory)
    }
    
    "doesn't delete non-existing directory" in new NoDirectoryContext {
      directoryCleaner.createCleanDirectory(directory) must not(throwA[Exception])
    }

  }

  trait DirectoryCleanerContext extends Before {

    val directory = Paths.get("dirname")
    val directoryCleaner = new TestDirectoryCleaner

    protected def dirContents: Map[Path, Seq[Path]] = Map((directory -> Seq.empty))

    override def before = directoryCleaner.initFileSystem(dirContents)

  }

  trait DirectoryWithContent extends DirectoryCleanerContext {
    val file = Paths.get("file")
    val subdir = Paths.get("subdir")
    val subdirFile = Paths.get("file in subdir")

    override protected def dirContents = Map(
      (directory -> Seq(file, subdir)),
      (subdir -> Seq(subdirFile)))

  }
  
  trait NoDirectoryContext extends DirectoryCleanerContext {
    override protected def dirContents = Map.empty
  }

  class TestDirectoryCleaner extends DirectoryCleaner {

    override val fileSystem = smartMock[FileSystem]

    def initFileSystem(directoryContents: Map[Path, Seq[Path]]) =
      directoryContents.foreach {
        case (dir, contents) =>
          fileSystem.listContents(dir) returns contents
          fileSystem.isDirectory(dir) returns true

      }

  }
}