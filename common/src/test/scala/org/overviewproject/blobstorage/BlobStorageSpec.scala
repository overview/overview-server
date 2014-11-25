package org.overviewproject.blobstorage

import java.io.InputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class BlobStorageSpec extends Specification with Mockito {
  "BlobStorage" should {
    trait BaseScope extends Scope {
      val mockConfig = mock[BlobStorageConfig]
      val mockStrategyFactory = mock[StrategyFactory]
      object TestBlobStorage extends BlobStorage {
        override protected val config = mockConfig
        override protected val strategyFactory = mockStrategyFactory
      }
    }

    "#get" should {
      "resolve a BlobStorageStrategy from the location" in new BaseScope {
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.get("s3:foo:bar")
        there was one(mockStrategyFactory).forLocation("s3:foo:bar")
      }

      "call BlobStorageStrategy#get" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.get("s3:foo:bar")
        there was one(mockStrategy).get("s3:foo:bar")
      }
    }

    "#delete" should {
      "resolve a BlobStorageStrategy from the location" in new BaseScope {
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.delete("s3:foo:bar")
        there was one(mockStrategyFactory).forLocation("s3:foo:bar")
      }

      "call BlobStorageStrategy#get" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.delete("s3:foo:bar")
        there was one(mockStrategy).delete("s3:foo:bar")
      }
    }

    "#create" should {
      "find the location prefix from config" in new BaseScope {
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.create(BlobBucketId.PageData, mock[InputStream], 100)
        there was one(mockConfig).getPreferredPrefix(BlobBucketId.PageData)
      }

      "find the strategy from the location prefix" in new BaseScope {
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.create(BlobBucketId.PageData, mock[InputStream], 100)
        there was one(mockStrategyFactory).forLocation("s3:foo")
      }

      "call create() with the location prefix and strategy" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        val mockInputStream = mock[InputStream]
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.create(BlobBucketId.PageData, mockInputStream, 100)
        there was one(mockStrategy).create("s3:foo", mockInputStream, 100)
      }
    }
  }
}
