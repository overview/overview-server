package org.overviewproject.sbt.assetbundler

import com.typesafe.config.{Config,ConfigFactory}
import java.io.File
import java.security.MessageDigest
import javax.xml.bind.annotation.adapters.HexBinaryAdapter
import sbt.{GlobFilter,PathFinder}

/** An asset bundle.
  *
  * An asset bundle is a compiled file made by concatinating source files.
  * The files are ordered and unique, and they can be specified as globs. For
  * instance, given the globs "vendor/jquery-1.8.1.js", "vendor/\*.coffee" and
  * "vendor/\*.js", a Bundle will point to jquery-1.8.1.js first, then all
  * .coffee files under vendor/, then all .js files under vendor/ which aren't
  * jquery-1.8.1.js.
  *
  * @constructor Create a new AssetBundle.
  * @param sourceRoot Root directory of source files.
  * @param destinationRoot Root directory of destination files.
  * @param bundleType Bundle type, e.g. "javascripts".
  * @param bundleKey Name of the bundle.
  * @param paths List of paths to source files.
  */
class AssetBundle(
    val sourceRoot: File,
    val destinationRoot: File,
    val bundleType: String,
    val bundleKey: String,
    val paths: Seq[String]) {
  private lazy val md5 = MessageDigest.getInstance("MD5")

  private lazy val pathFinder : PathFinder = {
    val bundleRoot = PathFinder(sourceRoot) / bundleType

    // Map from "vendor/*.js" to assetRoot / "vendor" / "*.js"
    val bundlePathFinders : Seq[PathFinder] = paths.map({ path =>
      path.split("/").foldLeft(bundleRoot)({ (a: PathFinder, b: String) =>
        if (b.contains("*")) {
          a * GlobFilter(b)
        } else {
          a / b
        }
      })
    })

    bundlePathFinders.reduce(_ +++ _)
  }

  private lazy val outputExtension = if (bundleType == "javascripts") "js" else "css"

  private def sourceToDestination(sourceFile: File) : File = {
    val sourceURI = sourceRoot.toURI

    val relativeURI = sourceURI.relativize(sourceFile.toURI).toString
    val relativeURIWithExtension = relativeURI.replaceFirst("""[^\.]+$""", outputExtension)

    val path = Seq(destinationRoot, "asset-bundler", bundleType, bundleKey, relativeURIWithExtension).mkString(File.separator)

    new File(path)
  }

  /**
   * List of source files in the bundle.
   */
  lazy val sourceFiles : Seq[File] = {
    pathFinder.get.distinct
  }

  /**
   * List of (source file, destination file) pairs.
   *
   * "Destination" files are interim files. For instance, a ".coffee" file will
   * have a ".js" destination. Do not confuse this with the "Output" file,
   * which is the final file that will be produced.
   */
  lazy val sourceAndDestinationFiles : Seq[(File,File)] = sourceFiles.map(f => (f, sourceToDestination(f)))

  private lazy val outputFileMainPart : String = {
    Seq(destinationRoot, bundleType, bundleKey).mkString(File.separator)
  }

  /**
   * File which should be written at the end of bundling.
   */
  lazy val outputFile : File = new File(outputFileMainPart + "." + outputExtension)

  /**
   * File which a minimized version of the bundle will be written to at the
   * end of bundling.
   *
   * This will crash if outputFile does not exist.
   */
  def minimizedOutputFile : File = new File(outputFileMainPart + "-" + hash + ".min." + outputExtension)

  private def fileContents(file: File) : String = {
    val source = scala.io.Source.fromFile(file)
    val contents = source.mkString
    source.close
    contents
  }

  /**
   * Returns an md5 hash of the given string.
   *
   * This only works after the caller has written to outputFile.
   */
  lazy val hash : String = {
    val contents = fileContents(outputFile)
    val bytes = md5.digest(contents.getBytes)
    (new HexBinaryAdapter).marshal(bytes).toLowerCase()
  }
}
