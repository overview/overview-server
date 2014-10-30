package models.archive

import scala.collection.immutable.HashSet

/**
 * Ensures that the filenames in the collection of `entries` are unique (case-insensitively), and 
 * do not contain illegal characters.
 * @todo remove illegal characters
 */
class ArchiveEntryCollection(entries: Seq[ArchiveEntry]) {

  def sanitizedEntries: Seq[ArchiveEntry] = {

    val (_, validEntries) = entries.foldLeft((HashSet[String](), Seq[ArchiveEntry]())) { (u, e) =>
      val (existingNames, existingEntries) = u
      val entry = if (isDuplicate(existingNames, e.name))
        e.copy(name = uniqueName(existingNames, e.name, 1))
      else e

      (addName(existingNames, entry.name), existingEntries :+ entry)
    }

    validEntries
  }

  // Try to find a unique name in the form "name (n)", where n is the first available integer
  // If there are >= 2 * Int.MaxValue names of the same form, "name (0)" is returned, which
  // may lead to duplicate filenames. That many files will probably cause other problems.
  private def uniqueName(existingNames: HashSet[String], duplicate: String, attempt: Int): String = {
    val nameAttempt = addAttemptToName(duplicate, attempt)

    if (!isDuplicate(existingNames, nameAttempt) || attempt == 0) nameAttempt
    else uniqueName(existingNames, duplicate, attempt + 1)
  }

  // store and compare names as lower case so we can match case insensitively
  private def addName(existingNames: HashSet[String], name: String): HashSet[String]  =
    existingNames + name.toLowerCase
    
  private def isDuplicate(existingNames: HashSet[String], name: String): Boolean =
    existingNames.contains(name.toLowerCase)
    
  private def addAttemptToName(name: String, attempt: Int): String = {
    val nameWithOptionalExtension = "^(.*?)(\\.[^.]*)?$".r
    
    name match {
      case nameWithOptionalExtension(base, null) => s"$base ($attempt)"
      case nameWithOptionalExtension(base, ext) => s"$base ($attempt)$ext"
    }
  }
}