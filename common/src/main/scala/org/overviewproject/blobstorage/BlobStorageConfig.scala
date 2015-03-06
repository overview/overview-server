package org.overviewproject.blobstorage

import com.amazonaws.auth.{AWSCredentials,DefaultAWSCredentialsProviderChain}
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
    * This uses DefaultAWSCredentialsProviderChain. That means you can set
    * AWS_ACCESS_KEY_ID/AWS_SECRET_KEY environment variables or simply run
    * Overview from an EC2 machine that has an IAM role.
    */
  def awsCredentials: AWSCredentials = {
    new DefaultAWSCredentialsProviderChain().getCredentials
  }
}

object BlobStorageConfig extends BlobStorageConfig {
  override val config = ConfigFactory.load()
}
