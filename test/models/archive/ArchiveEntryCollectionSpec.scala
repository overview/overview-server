package models.archive

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class ArchiveEntryCollectionSpec extends Specification {

  "ArchiveEntryCollection" should {

    "return entries with valid filenames unchanged" in new ArchiveEntryCollectionContext {
      val collection = createCollection(uniqueNames)

      val names = collection.sanitizedEntries.map(_.name)

      names must containTheSameElementsAs(uniqueNames)
    }

    "transform filenames to be unique" in new ArchiveEntryCollectionContext {
      val collection = createCollection(uniqueNames ++ uniqueNames)
      val transformedNames = uniqueNames.map(_ + " (1)")

      val names = collection.sanitizedEntries.map(_.name)
      names must containTheSameElementsAs(uniqueNames ++ transformedNames)
    }

    "increment until unique file name is found" in new ArchiveEntryCollectionContext {

      val collection = createCollection(collidingNames :+ base)

      val names = collection.sanitizedEntries.map(_.name)

      names must containTheSameElementsAs(collidingNames :+ s"$base ($numberOfEntries)")
    }

    "handle files with unique file name format" in new ArchiveEntryCollectionContext {
      val nameWithFormat = s"$base (1)"
      val collection = createCollection(nameWithFormat +: collidingNames)

      val names = collection.sanitizedEntries.map(_.name)

      names must containTheSameElementsAs(collidingNames :+ s"$nameWithFormat (1)")
    }

    "be case-insensitive when checking for duplicate names" in new ArchiveEntryCollectionContext {
      val duplicates = uniqueNames.map(_.toUpperCase)
      val expectedTransformedNames = duplicates.map(_ + " (1)")

      val collection = createCollection(uniqueNames ++ duplicates)

      val names = collection.sanitizedEntries.map(_.name)

      names must containTheSameElementsAs(uniqueNames ++ expectedTransformedNames)
    }

    "be case-insensitive when checking unique file name format" in new ArchiveEntryCollectionContext {
      val sensitiveNames = Seq("file (1)", "file")
      val conflictingName = "File"

      val collection = createCollection(sensitiveNames :+ conflictingName)

      val names = collection.sanitizedEntries.map(_.name)

      names must containTheSameElementsAs(sensitiveNames :+ s"$conflictingName (2)")
    }

    "add disambiguator before file extension" in new ArchiveEntryCollectionContext {
      val namesWithExtensions = Seq("file.pdf", "file (1).pdf", "file.doc", "File.pdf")

      val collection = createCollection(namesWithExtensions)

      val names = collection.sanitizedEntries.map(_.name)

      names must containTheSameElementsAs(Seq("file.pdf", "file (1).pdf", "file.doc", "File (2).pdf"))
    }

    "replace illegal characters" in new ArchiveEntryCollectionContext {
      val badChars = """<>:"/\|?*"""

      val replacedChars = "_" * badChars.size

      val collection = createCollection(Seq(badChars))

      val names = collection.sanitizedEntries.map(_.name)

      names.head must be equalTo replacedChars
    }

    "replace control characters" in new ArchiveEntryCollectionContext {
      val controlCharacters = Array.tabulate(32)(_.toByte)
      val badName = new String(controlCharacters)
      val replacedChars = "_" * 32

      val collection = createCollection(Seq(badName))

      val names = collection.sanitizedEntries.map(_.name)
      
      names.head must be equalTo replacedChars

    }
  }
}

trait ArchiveEntryCollectionContext extends Scope {
  val numberOfEntries = 5
  val fileSize = 100
  def stream = new ByteArrayInputStream(Array.empty[Byte])

  val base = "file"
  val uniqueNames = Seq.tabulate(numberOfEntries)(n => s"$base-$n")
  val collidingNames = base +: Seq.tabulate(numberOfEntries)(n => s"$base ($n)")

  def createCollection(names: Seq[String]): ArchiveEntryCollection =
    new ArchiveEntryCollection(names.map(ArchiveEntry(_, fileSize, stream _)))
}