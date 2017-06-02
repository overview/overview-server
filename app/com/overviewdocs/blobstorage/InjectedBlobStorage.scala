package com.overviewdocs.blobstorage

import javax.inject.{Inject,Singleton}

/** A BlobStorage, ready for dependency-injection.
  *
  * This exists because our `common` package doesn't use DI.
  */
@Singleton
class InjectedBlobStorage @Inject() extends BlobStorage {
  override val config = BlobStorageConfig
  override val strategyFactory = StrategyFactory
}
