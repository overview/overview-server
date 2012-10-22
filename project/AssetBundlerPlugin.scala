package org.overviewproject.sbt.assetbundler

import java.io.File
import sbt._
import sbt.Project.Initialize
import sbt.Keys.{cleanFiles,compile,resourceGenerators,resourceManaged,sourceDirectory}

object AssetBundlerPlugin extends Plugin {
  import Keys._

  object Keys {
    val assetBundler = TaskKey[Seq[File]]("asset-bundler", "Compile asset bundles")
    val configFile = SettingKey[File]("asset-bundler-config-file", "File detailing asset bundles to compile")
  }

  private def loadAssetBundleConfig(configFile: File) : AssetBundleConfig = {
    new AssetBundleConfig(configFile)
  }

  private def loadAssetBundles(configFile: File, sourceDirectory: File, destinationDirectory: File) : Seq[AssetBundle] = {
    loadAssetBundleConfig(configFile).loadBundles(sourceDirectory, destinationDirectory)
  }

  private def compileBundleIfChanged(bundle: AssetBundle) : Unit = {
    val files : Seq[(File,File)] = bundle.sourceAndDestinationFiles
    ScriptCompiler.compileAllChangedOrThrow(files)

    val destinationFiles = files.map(_._2)
    val output = bundle.outputFile
    ScriptCompiler.concatenateDestinationFiles(destinationFiles, output)
    val minimizedOutput = bundle.minimizedOutputFile
    ScriptCompiler.compileDestinationFiles(destinationFiles, minimizedOutput)
  }

  private val assetBundlesTask : Initialize[Task[Seq[File]]] =
    (configFile in assetBundler,
     sourceDirectory in assetBundler,
     resourceManaged in assetBundler) map
  {
    (configFile, sourceDirectory, destinationDirectory) => {
      val bundles = loadAssetBundles(configFile, sourceDirectory, destinationDirectory)
      bundles.foreach(compileBundleIfChanged(_))
      bundles.map(_.outputFile) ++ bundles.map(_.minimizedOutputFile)
    }
  }

  private def assetSettingsIn(c: Configuration) : Seq[Setting[_]] =
    inConfig(c)(assetSettings0 ++ Seq(
      sourceDirectory in assetBundler <<= (sourceDirectory in c) { _ / "assets" },
      resourceManaged in assetBundler <<= (resourceManaged in c) { _ / "public" }
    )) ++ Seq(
      cleanFiles <+= (resourceManaged in assetBundler in c),
      resourceGenerators in c <+= assetBundler in c,
      compile in c <<= (compile in c).dependsOn(assetBundler in c)
    )

  private def assetSettings0 : Seq[Setting[_]] = Seq(
    configFile := file("conf/assets.conf"),
    assetBundler <<= assetBundlesTask
  )

  def assetSettings : Seq[Setting[_]] = assetSettingsIn(Compile)
}
