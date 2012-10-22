package org.overviewproject.sbt.assetbundler

import com.typesafe.config.{Config,ConfigFactory}
import java.io.File
import scala.collection.JavaConversions._

class AssetBundleConfig(val configFile: File) {
  val config: Config = ConfigFactory.parseFile(configFile)

  /**
   * @param bundleType e.g., "javascripts"
   * @param bundleKey e.g., "defaults"
   * @returns An AssetBundle.
   */
  private def loadBundle(sourceRoot: File, destinationRoot: File, bundleType: String, bundleKey: String) : AssetBundle = {
    val bundlesConfig : Config = config.getConfig(bundleType)
    val bundleFilenames : Seq[String] = bundlesConfig.getStringList(bundleKey)

    new AssetBundle(sourceRoot, destinationRoot, bundleType, bundleKey, bundleFilenames)
  }

  /**
   * Returns a list of bundleTypes (e.g., "javascripts")
   */
  def listBundleTypes() : Seq[String] = {
    Seq("javascripts", "stylesheets").filter(k => config.hasPath(k))
  }

  /**
   * @param bundleType A bundle type, e.g. "javascripts"
   * @return A list of bundle keys
   */
  def listBundlesOfType(bundleType: String) : Seq[String] = {
    config.getConfig(bundleType).root.keys.toSeq
  }

  def loadBundles(sourceRoot: File, destinationRoot: File) : Seq[AssetBundle] = {
    val bundlesByType : Seq[Seq[AssetBundle]] = listBundleTypes.map({ bundleType => 
      listBundlesOfType(bundleType).map(loadBundle(sourceRoot, destinationRoot, bundleType, _))
    })

    bundlesByType.flatten
  }
}
