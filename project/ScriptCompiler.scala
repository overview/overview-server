package org.overviewproject.sbt.assetbundler

import coffeescript.Vanilla
import java.io.{File,BufferedWriter,FileWriter}
import com.google.javascript.jscomp.{Compiler,CompilerOptions,JSSourceFile,CompilationLevel}

object ScriptCompiler {
  case class CompilationException(val file: Option[File], val message: String, val line: Int, val column: Int) extends Exception(message) {
    override def toString =  "in " + file.getOrElse("") + " - " + super.toString()
  }

  private def fileContents(file: File) : String = {
    val source = io.Source.fromFile(file)
    val contents = source.mkString
    source.close
    contents
  }

  private def ensureParentDirectoryExists(file: File) : Unit = {
    Option(file.getParentFile()).map(_.mkdirs)
  }

  private def dumpStringToFile(contents: String, file: File) : Unit = {
    ensureParentDirectoryExists(file)
    val writer = new BufferedWriter(new FileWriter(file))
    writer.write(contents)
    writer.close
  }

  /**
   * @param File a .js or .coffee file
   * @returns Either an error string, or JavaScript belonging in a file.
   */
  def compile(file: File) : Either[String,String] = {
    val contents = fileContents(file)

    if (file.toPath.toString.endsWith(".coffee")) {
      Vanilla.compile(contents, false)
    } else {
      Right(fileContents(file))
    }
  }

  /**
   * @returns true if source is newer than destination, or if destination does
   *          not exist.
   */
  private def needToRecompile(source: File, destination: File) : Boolean = {
    // if destination does not exist, lastModified = 0L
    source.lastModified > destination.lastModified
  }

  def compileAllChangedOrThrow(sourceToDest: Seq[(File,File)]) : Unit = {
    sourceToDest.foreach({ tuple =>
      val (source, destination) = tuple

      if (needToRecompile(source, destination)) {
        compile(source).fold(
          { err: String => throw new CompilationException(Some(source), err, 0, 0) }, // TODO find line/column
          { js: String => dumpStringToFile(js, destination) }
        )
      }
    })
  }

  def concatenateDestinationFiles(files: Seq[File], output: File) : Unit = {
    val contents = files.map(fileContents(_)).mkString
    dumpStringToFile(contents, output)
  }

  def compileDestinationFiles(files: Seq[File], output: File) : Unit = {
    val sourceFiles : Seq[JSSourceFile] = files.map(JSSourceFile.fromFile(_))
    val compiler = new Compiler()
    val options = new CompilerOptions()
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options)

    compiler.compile(Seq[JSSourceFile]().toArray, sourceFiles.toArray, options)
    val messages = compiler.getErrors.toSeq ++ compiler.getWarnings.toSeq

    if (messages.isEmpty) {
      val contents = compiler.toSource()
      dumpStringToFile(contents, output)
    } else {
      val error = messages(0)
      throw new CompilationException(None, error.description, error.lineNumber, 0)
    }
  }
}
