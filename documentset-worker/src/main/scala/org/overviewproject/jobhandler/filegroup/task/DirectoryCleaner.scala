package org.overviewproject.jobhandler.filegroup.task

import java.nio.file.Path

trait DirectoryCleaner {

  def createCleanDirectory(directory: Path): Unit = {
    deleteDirectory(directory)
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