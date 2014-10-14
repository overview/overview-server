package models.archive

import scala.collection.immutable.HashSet

class ArchiveEntryCollection(entries: Seq[ArchiveEntry]) {

  def sanitizedEntries: Seq[ArchiveEntry] = {
    
   val (_, validEntries) =  entries.foldLeft((HashSet[String](), Seq[ArchiveEntry]())) { (u, e) =>
     val (existingNames, existingEntries) = u
      val entry = if (existingNames.contains(e.name)) 
        e.copy(name = uniqueName(existingNames, e.name, 1))
      else e
     
      (existingNames + entry.name, existingEntries :+ entry)
   }
    
    validEntries
  }
  

  // Try to find a unique name in the form "name (n)", where n is the first available integer
  // If there are >= 2 * Int.MaxValue names of the same form, "name (0)" is returned, which
  // may lead to duplicate filenames. That many files will probably cause other problems.
  private def uniqueName(existingNames: HashSet[String], duplicate: String, attempt: Int): String = {
    val nameAttempt = s"$duplicate ($attempt)"
    
    if (!existingNames.contains(s"$duplicate ($attempt)") || attempt == 0) nameAttempt
    else uniqueName(existingNames, duplicate, attempt + 1)
  }
  
}