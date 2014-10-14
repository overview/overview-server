package models.archive

class ArchiveEntryCollection(entries: Seq[ArchiveEntry]) {

  def sanitizedEntries: Seq[ArchiveEntry] = {
    
   val validEntries =  entries.foldLeft(Map[String, ArchiveEntry]()) { (u, e) =>
      val entry = if (u.contains(e.name)) {
        val uniqueName = e.name + " (1)"
        e.copy(name = uniqueName)
      }
      else e
     
      u + (entry.name -> entry)
   }
    
    validEntries.values.toSeq
  }
  
}