package org.overviewproject.sbt.assetbundler

import coffeescript.Vanilla
import java.io.File
import com.google.javascript.jscomp.{Compiler,CompilerOptions,JSSourceFile,CompilationLevel}

object ScriptCompiler {
  private lazy val utf8 = java.nio.charset.Charset.forName("utf-8")
  
  case class CompilationException(val file: Option[File], val message: String, val line: Int, val column: Int) extends Exception(message) {
    override def toString =  "in " + file.getOrElse("") + " - " + super.toString()
  }

  /** Compiles a CoffeeScript string.
    *
    * @return JavaScript produced from the CoffeeScript input.
    */
  def compileCoffeeScript(contents: String, file: File) : String = {
    Vanilla.compile(contents, false).fold(
      { err: String => throw new CompilationException(Some(file), err, 0, 0) }, // TODO find line/column
      { js: String => js }
    )
  }

  /** Concatenates and compresses a list of JavaScript files.
    *
    * @return A string of compressed JavaScript code.
    */
  def compileFiles(files: Seq[File]) : String = {
    val sourceFiles : Seq[JSSourceFile] = files.map(JSSourceFile.fromFile(_, utf8))
    val compiler = new Compiler()
    val options = new CompilerOptions()
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options)

    compiler.compile(Seq[JSSourceFile]().toArray, sourceFiles.toArray, options)
    val messages = compiler.getErrors.toSeq ++ compiler.getWarnings.toSeq

    if (messages.isEmpty) {
      compiler.toSource()
    } else {
      val error = messages(0)
      throw new CompilationException(None, error.description, error.lineNumber, 0)
    }
  }
}
