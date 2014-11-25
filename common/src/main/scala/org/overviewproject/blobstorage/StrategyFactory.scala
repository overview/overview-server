package org.overviewproject.blobstorage

trait StrategyFactory {
  def forLocation(s: String): BlobStorageStrategy = {
    val strategyId = s.substring(0, s.indexOf(":"))

    strategyId match {
      case "s3" => S3Strategy
      case "pglo" => PgLoStrategy
      case "pagebytea" => PageByteAStrategy
      case "file" => FileStrategy
      case _ => throw new IllegalArgumentException(strategyId + " is not a valid strategy ID")
    }
  }
}

object StrategyFactory extends StrategyFactory
