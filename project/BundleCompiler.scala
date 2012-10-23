package org.overviewproject.sbt.assetbundler

import java.io.{File,BufferedWriter,FileWriter}
import play.core.less.LessCompiler

/** A compiler that creates AssetBundle output files.
 *
 * Create a compiler using AssetBundle.createCompilerForBundle(), then call
 * compile() on it to refresh the output files. Unchanged files won't be
 * recompiled.
 */
trait BundleCompiler {
  val bundle : AssetBundle

  /** Compiles one file and writes the result to destination. */
  def compileOne(source: File, destination: File) : Unit

  /** Concatenates and minifies compiled files. */
  def compressFiles(files: Seq[File], destination: File) : Unit = {
    concatenateFiles(files, destination)
  }

  /** @return A string with the contents of the given file. */
  protected def fileContents(file: File) : String = {
    val source = io.Source.fromFile(file)
    val contents = source.mkString
    source.close
    contents
  }

  private def ensureParentDirectoryExists(file: File) : Unit = {
    Option(file.getParentFile()).map(_.mkdirs)
  }

  protected def dumpStringToFile(contents: String, file: File) : Unit = {
    ensureParentDirectoryExists(file)
    val writer = new BufferedWriter(new FileWriter(file))
    writer.write(contents)
    writer.close
  }

  def concatenateFiles(files: Seq[File], output: File) : Unit = {
    val contents = files.map(fileContents(_)).mkString
    dumpStringToFile(contents, output)
  }

  /**
    * @return true if source is newer than destination, or if destination does
    *         not exist.
    */
  private def needToRecompile(source: File, destination: File) : Boolean = {
    // if destination does not exist, lastModified = 0L
    source.lastModified > destination.lastModified
  }

  /**
    * @param files Seq of (source,destination) Files.
    * @return true if any source files have changed since the last compile.
    */
  private def needToRecompileAny(files: Seq[(File,File)]) : Boolean = {
    files.exists({ tuple => needToRecompile(tuple._1, tuple._2) })
  }

  private def compileChangedFiles(files: Seq[(File,File)]) : Unit = {
    files.foreach({ tuple =>
      val (source, destination) = tuple

      if (needToRecompile(source, destination)) {
        compileOne(source, destination)
      }
    })
  }

  def compile() : Unit = {
    val files : Seq[(File,File)] = bundle.sourceAndDestinationFiles

    if (needToRecompileAny(files)) {
      compileChangedFiles(files)

      val destinationFiles = files.map(_._2)
      val output = bundle.outputFile
      concatenateFiles(destinationFiles, output)

      val minimizedOutput = bundle.minimizedOutputFile
      compressFiles(destinationFiles, minimizedOutput)
    }
  }
}

class ScriptBundleCompiler(val bundle: AssetBundle) extends BundleCompiler {
  def compileOne(source: File, destination: File) : Unit = {
    val contents = fileContents(source)
    val filename = source.toPath.toString

    val output = if (filename.endsWith(".coffee")) {
      ScriptCompiler.compileCoffeeScript(contents, source)
    } else {
      contents
    }

    dumpStringToFile(output, destination)
  }

  override def compressFiles(files: Seq[File], destination: File) : Unit = {
    val output = ScriptCompiler.compileFiles(files)
    dumpStringToFile(output, destination)
  }
}

class StyleBundleCompiler(val bundle: AssetBundle) extends BundleCompiler {
  def compileOne(source: File, destination: File) : Unit = {
    val output = LessCompiler.compile(source)._1
    dumpStringToFile(output, destination)
  }
}

object BundleCompiler {
  def apply(bundle: AssetBundle) : BundleCompiler = {
    if (bundle.bundleType == "javascripts") {
      new ScriptBundleCompiler(bundle)
    } else {
      new StyleBundleCompiler(bundle)
    }
  }
}
