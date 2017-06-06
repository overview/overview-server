package com.overviewdocs.blobstorage

import java.io.{ File, IOException, InputStream }
import java.nio.file.{ Files, Path }
import scala.concurrent.Future
import java.nio.charset.StandardCharsets
import scala.io.Source

class FileStrategySpec extends StrategySpecification {
  trait FileBaseScope extends BaseScope {
    val tmpDir: Path = Files.createTempDirectory("overview-file-strategy-spec")

    def rimraf(path: Path): Unit = {
      import java.nio.file._
      import java.nio.file.attribute.BasicFileAttributes
      val deleteVisitor = new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(file: Path, ioe: IOException): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
      }
      Files.walkFileTree(path, deleteVisitor)
    }

    override def after = {
      if (tmpDir.toFile.exists) { // We delete it before this sometimes
        rimraf(tmpDir)
      }
      super.after
    }

    val mockConfig = mock[BlobStorageConfig]
    mockConfig.fileBaseDirectory returns tmpDir.toString
    object TestFileStrategy extends FileStrategy {
      override val config = mockConfig
    }

    def invalidLocationThrowsException[T](f: String => T) = {
      (f("fil:BUCKET:KEY") must throwA[IllegalArgumentException]) and
        (f("file::key") must throwA[IllegalArgumentException]) and
        (f("file:bucket:") must throwA[IllegalArgumentException])

    }

    // Create bucket1
    val bucket = "bucket1"
    val bucketFile = new File(tmpDir.toString, bucket)
    bucketFile.mkdir()
  }

  trait ExistingFileScope extends FileBaseScope {
    // Create key1
    val key = "key1"
    val keyFile = new File(bucketFile.toString, key)

    Files.write(keyFile.toPath, "data1".getBytes("utf-8"))
  }

  "#get" should {

    "throw an exception when get location does not look like file:BUCKET:KEY" in new ExistingFileScope {
      invalidLocationThrowsException(TestFileStrategy.get)
    }

    // FIXME uncomment and fix these tests
    //"throw a delayed exception when the key does not exist in the bucket which does" in new ExistingFileScope {
    //  val future = TestFileStrategy.get(s"file:$bucket:x$key")
    //  await(future) must throwA[IOException]
    //}

    //"throw a delayed exception when the bucket does not exist" in new ExistingFileScope {
    //  val future = TestFileStrategy.get(s"file:x$bucket:$key")
    //  await(future) must throwA[IOException]
    //}

    //"throw a delayed exception when the base directory does not exist" in new ExistingFileScope {
    //  rimraf(tmpDir)
    //  val future = TestFileStrategy.get(s"file:$bucket:$key")
    //  await(future) must throwA[IOException]
    //}
//
//    "return an Enumerator of the file" in new ExistingFileScope {
//      val future = TestFileStrategy.get(s"file:$bucket:$key")
//      val enumerator = await(future)
//      val byteArray = consume(enumerator)
//      new String(byteArray, "utf-8") must beEqualTo("data1")
//    }
  }

  "#delete" should {
    "throw an exception when delete location does not look like file:BUCKET:KEY" in new ExistingFileScope {
      invalidLocationThrowsException(TestFileStrategy.delete)
    }

    "succeed when the key does not exist in the bucket which does" in new ExistingFileScope {
      val future = TestFileStrategy.delete(s"file:$bucket:x$key")
      await(future) must beEqualTo(())
    }

    "succeed when the bucket does not exist" in new ExistingFileScope {
      val future = TestFileStrategy.delete(s"file:x$bucket:$key")
      await(future) must beEqualTo(())
    }

    "succeed when the base directory does not exist" in new ExistingFileScope {
      rimraf(tmpDir)
      val future = TestFileStrategy.delete(s"file:$bucket:$key")
      await(future) must beEqualTo(())
    }

    "delete the file" in new ExistingFileScope {
      val future = TestFileStrategy.delete(s"file:$bucket:$key")
      await(future) must beEqualTo(())
      keyFile.exists must beFalse
    }
  }

  "#create" should {
    trait CreateScope extends FileBaseScope {
      val locationRegex = s"^file:$bucket:([-\\w]+)$$".r

      val toCreate: Path = tempFile("foo")

      def fileAtLocation(location: String): Path = location match {
        case locationRegex(key) => new File(bucketFile, key).toPath
      }
    }

    "throw an exception when create location does not look like file:BUCKET:KEY" in new CreateScope {
      invalidLocationThrowsException(TestFileStrategy.create(_, toCreate))
    }

    "return location" in new CreateScope {
      val future = TestFileStrategy.create(s"file:$bucket", toCreate)
      val location = await(future)

      location must beMatching(locationRegex)
    }

    "write to the file" in new CreateScope {
      val location = await(TestFileStrategy.create(s"file:$bucket", toCreate))
      val path = fileAtLocation(location)

      path.toFile.exists must beTrue
      new String(Files.readAllBytes(path), "utf-8") must beEqualTo("foo")
      path must not(beEqualTo(toCreate))
    }

    "create any missing directories in path" in new CreateScope {
      rimraf(tmpDir)

      val future = TestFileStrategy.create(s"file:$bucket", toCreate)
      val location = await(future)

      fileAtLocation(location).toFile.exists must beTrue
    }
  }
}
