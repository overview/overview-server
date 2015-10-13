package com.overviewdocs.blobstorage

trait StrategyFactory {
  def forLocation(s: String): BlobStorageStrategy = {
    val strategyId: String = s.split(":")(0)

    strategyId match {
      case "s3" => S3Strategy
      case "pglo" => PgLoStrategy
      case "file" => FileStrategy
      case _ => throw new IllegalArgumentException(strategyId + " is not a valid strategy ID")
    }
  }
}

object StrategyFactory extends StrategyFactory
