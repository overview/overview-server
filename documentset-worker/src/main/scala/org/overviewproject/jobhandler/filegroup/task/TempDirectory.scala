package org.overviewproject.jobhandler.filegroup.task

import java.nio.file.Path
import java.nio.file.Paths

object TempDirectory {

  def path: Path = {
    val tmpDir = System.getProperty("java.io.tmpdir")
    Paths.get(tmpDir, "overview-documentset-worker")
  }
  
  def filePath(name: String): Path = path.resolve(name)

  def create: Unit = DirectoryCleaner.createCleanDirectory(path)
}