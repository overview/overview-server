package modules

import play.api.{Configuration,Environment}
import play.api.inject.{Binding,Module}
import javax.inject.{Inject,Singleton}

import com.overviewdocs.blobstorage.{BlobStorage,BlobStorageConfig,StrategyFactory}
import com.overviewdocs.util.Logger

/** Provides a BlobStorage through runtime dependency injection.
  *
  * Usage, in your class:
  *
  * class Doer @Inject() (blobStorage: BlobStorage) {
  *   ...
  * }
  *
  * If you aren't using runtime dependency injection, just use the `BlobStorage`
  * singleton object instead.
  */
@Singleton
class BlobStorageModule extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(bind[BlobStorage].to[InjectedBlobStorage].in[Singleton])
  }
}

class InjectedBlobStorage @Inject() extends BlobStorage {
  override protected val config = BlobStorageConfig
  override protected val strategyFactory = StrategyFactory
  override protected val logger = Logger.forClass(getClass)
}
