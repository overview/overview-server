package models.archive

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.ByteArrayInputStream

class ArchiveEntryCollectionSpec extends Specification {

  "ArchiveEntryCollection" should {
    
    inExample("return entries with valid filenames unchanged") in new ArchiveEntryCollectionContext {
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
  }
}


trait ArchiveEntryCollectionContext extends Scope {
  val numberOfEntries = 5
  val fileSize = 100
  def stream = new ByteArrayInputStream(Array.empty[Byte])
  
  val uniqueNames = Seq.tabulate(numberOfEntries)(n => s"file-$n")
  
  def createCollection(names: Seq[String]): ArchiveEntryCollection = 
    new ArchiveEntryCollection(names.map(ArchiveEntry(_, fileSize, stream _)))
}