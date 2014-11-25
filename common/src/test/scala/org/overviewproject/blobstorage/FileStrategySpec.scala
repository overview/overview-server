package org.overviewproject.blobstorage

import java.io.{ File, IOException, InputStream }
import java.nio.file.{ Files, Path }
import org.specs2.mutable.After
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import scala.concurrent.Future

class FileStrategySpec extends StrategySpecification {
  trait FileBaseScope extends BaseScope with After {
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
    }
  }

  trait ExistingFileScope extends FileBaseScope {
    // Create bucket1/key1
    val bucket = "bucket1"
    val bucketFile = new File(tmpDir.toString, bucket)
    bucketFile.mkdir()

    val key = "key1"
    val keyFile = new File(bucketFile.toString, key)
    val content = "this is the content"
    private val io = utf8InputStream(content)
    Files.copy(io, keyFile.toPath)
    io.close()

    val mockConfig = mock[BlobStorageConfig]
    mockConfig.fileBaseDirectory returns tmpDir.toString
    object TestFileStrategy extends FileStrategy {
      override val config = mockConfig
    }

  }

  "#get" should {

    "throw an exception when the location does not look like file:BUCKET:KEY" in new ExistingFileScope {
      TestFileStrategy.get("fil:BUCKET:KEY") must throwA[IllegalArgumentException]
      TestFileStrategy.get("file::key") must throwA[IllegalArgumentException]
      TestFileStrategy.get("file:bucket:") must throwA[IllegalArgumentException]
    }

    "throw a delayed exception when the key does not exist in the bucket which does" in new ExistingFileScope {
      val future = TestFileStrategy.get(s"file:$bucket:x$key")
      await(future) must throwA[IOException]
    }

    "throw a delayed exception when the bucket does not exist" in new ExistingFileScope {
      val future = TestFileStrategy.get(s"file:x$bucket:$key")
      await(future) must throwA[IOException]
    }

    "throw a delayed exception when the base directory does not exist" in new ExistingFileScope {
      rimraf(tmpDir)
      val future = TestFileStrategy.get(s"file:$bucket:$key")
      await(future) must throwA[IOException]
    }

    "return an Enumerator of the file" in new ExistingFileScope {
      val future = TestFileStrategy.get(s"file:$bucket:$key")
      val enumerator = await(future)
      val byteArray = consume(enumerator)
      new String(byteArray, "utf-8") must beEqualTo(content)
    }
  }
  
  "#delete" should {
    "throw an exception when the location does not look like file:BUCKET:KEY" in new ExistingFileScope {
      TestFileStrategy.delete("fil:BUCKET:KEY") must throwA[IllegalArgumentException]
      TestFileStrategy.delete("file::key") must throwA[IllegalArgumentException]
      TestFileStrategy.delete("file:bucket:") must throwA[IllegalArgumentException]
    }

    "throw a delayed exception when the key does not exist in the bucket which does" in new ExistingFileScope {
      val future = TestFileStrategy.delete(s"file:$bucket:x$key")
      await(future) must throwA[IOException]
    }

    "throw a delayed exception when the bucket does not exist" in new ExistingFileScope {
      val future = TestFileStrategy.delete(s"file:x$bucket:$key")
      await(future) must throwA[IOException]
    }

    "throw a delayed exception when the base directory does not exist" in new ExistingFileScope {
      rimraf(tmpDir)
      val future = TestFileStrategy.delete(s"file:$bucket:$key")
      await(future) must throwA[IOException]
    }
    
    "delete the file" in new ExistingFileScope {
      val future = TestFileStrategy.delete(s"file:$bucket:$key")
      keyFile.exists must beFalse
    }
  }
}
