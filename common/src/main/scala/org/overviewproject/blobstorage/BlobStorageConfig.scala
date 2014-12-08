package org.overviewproject.blobstorage

import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.typesafe.config.{Config,ConfigFactory}

trait BlobStorageConfig {
  protected val config: Config

  /** The location pattern for new blobs in the bucket.
    *
    * For instance, given PageDataBlobBucketId, the return value might be
    * <tt>"s3:overview-page-data"</tt>.
    *
    * The configuration key is
    * <tt>blobStorage.preferredPrefixes.[bucket.id]</tt>.
    *
    * @throws ConfigException.Missing if the config value is missing
    * @throws ConfigException.WrongType if the config value is not a String
    */
  def getPreferredPrefix(bucket: BlobBucketId): String = {
    config.getString("blobStorage.preferredPrefixes." + bucket.id)
  }

  /** The base directory for the file storage strategy.
    *
    * For instance, this might be <tt>"./blob-storage/production"</tt>.
    *
    * The user must have write permission to the directory. If it does not
    * exist, it will be created on first use.
    *
    * The configuration key is
    * <tt>blobStorage.file.baseDirectory</tt>.
    *
    * @throws ConfigException.Missing if the config value is missing
    * @throws ConfigException.WrongType if the config value is not a String
    */
  def fileBaseDirectory: String = {
    config.getString("blobStorage.file.baseDirectory")
  }

  /** The credentials for AWS.
    *
    * This will only be called when trying to read from or write to S3.
    *
    * The configuration keys are <tt>blobStorage.s3.accessKeyId</tt> and
    * <tt>blobStorage.s3.secretKey</tt>.
    *
    * @throws ConfigException.Missing if a config value is missing
    * @throws ConfigException.WrongType if a config value is not a String
    */
  def awsCredentials: AWSCredentials = {
    val accessKeyId = config.getString("blobStorage.s3.accessKeyId")
    val secretKey = config.getString("blobStorage.s3.secretKey")

    new BasicAWSCredentials(accessKeyId, secretKey)
  }
}

object BlobStorageConfig extends BlobStorageConfig {
  override val config = ConfigFactory.load()
}
