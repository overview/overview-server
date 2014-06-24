package org.overviewproject.jobhandler.filegroup.task

import java.nio.file.Path
import java.nio.file.Files

trait DirectoryCleaner {

  def createCleanDirectory(directory: Path): Unit = {
    if (fileSystem.isDirectory(directory)) deleteDirectory(directory)
    fileSystem.createDirectory(directory)
  }

  private def deleteDirectory(directory: Path): Unit = {
    val contents = fileSystem.listContents(directory)

    contents.foreach { f =>
      if (fileSystem.isDirectory(f)) deleteDirectory(f)
      else fileSystem.deleteIfExists(f)
    }

    fileSystem.deleteIfExists(directory)
  }

  protected val fileSystem: FileSystem

  protected trait FileSystem {
    def createDirectory(directory: Path): Unit
    def deleteIfExists(directory: Path): Unit
    def listContents(directory: Path): Iterable[Path]
    def isDirectory(file: Path): Boolean
  }
}

object DirectoryCleaner extends DirectoryCleaner {
  import scala.collection.JavaConverters._

  override protected val fileSystem = new DefaultFileSystem

  class DefaultFileSystem extends FileSystem {
    def createDirectory(directory: Path): Unit = Files.createDirectory(directory)
    def deleteIfExists(directory: Path): Unit = Files.deleteIfExists(directory)
    def listContents(directory: Path): Iterable[Path] = Files.newDirectoryStream(directory).asScala
    def isDirectory(file: Path): Boolean = Files.isDirectory(file)
  }
}