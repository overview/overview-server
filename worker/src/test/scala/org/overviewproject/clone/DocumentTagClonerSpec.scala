package org.overviewproject.clone

import org.overviewproject.persistence.orm.{ Schema, Tag }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.{ Document, DocumentTag }
import org.overviewproject.tree.orm.DocumentType._
import org.squeryl.KeyedEntity
import org.overviewproject.persistence.DocumentSetIdGenerator

class DocumentTagClonerSpec extends DbSpecification {

  step(setupDb)

  "DocumentTagCloner" should {

    trait DocumentTagContext extends DbTestContext {
      var sourceDocumentSetId: Long = _
      var cloneDocumentSetId: Long = _
      var sourceDocumentTags: Seq[DocumentTag] = _
      var cloneDocumentTags: Seq[DocumentTag] = _
      
      var tagMapping: Map[Long, Long] = _

      def createDocumentTags(documents: Seq[Document], tags: Seq[Tag]): Seq[DocumentTag] = {
        documents.zip(tags).map { dt =>
          Schema.documents.insert(dt._1)
          Schema.tags.insert(dt._2)

          DocumentTag(dt._1.id, dt._2.id)
        }
      }

      def createMapping(source: Seq[KeyedEntity[Long]], clone: Seq[KeyedEntity[Long]]): Map[Long, Long] =
        source.map(_.id).zip(clone.map(_.id)).toMap

      override def setupWithDb = {
        sourceDocumentSetId = insertDocumentSet("DocumentTagClonerSpec")
        cloneDocumentSetId = insertDocumentSet("CloneDocumentTagClonerSpec")
        val sourceIds = new DocumentSetIdGenerator(sourceDocumentSetId)
        val cloneIds = new DocumentSetIdGenerator(cloneDocumentSetId)
        
        val sourceDocuments = Seq.tabulate(10)(i => Document(CsvImportDocument, sourceDocumentSetId, text = Some("text-" + i), id = sourceIds.next))
        val sourceTags = Seq.tabulate(10)(i => Tag(sourceDocumentSetId, "tag-i"))
        val cloneDocuments = sourceDocuments.map(_.copy(documentSetId = cloneDocumentSetId, id = cloneIds.next))
        val cloneTags = sourceTags.map(_.copy(documentSetId = cloneDocumentSetId))

        sourceDocumentTags = createDocumentTags(sourceDocuments, sourceTags)
        cloneDocumentTags = createDocumentTags(cloneDocuments, cloneTags)

        tagMapping = createMapping(sourceTags, cloneTags)
        
        Schema.documentTags.insert(sourceDocumentTags)
      }

    }

    "clone DocumentTags" in new DocumentTagContext {
      DocumentTagCloner.dbClone(sourceDocumentSetId, cloneDocumentSetId, tagMapping)

      val documentTags = Schema.documentTags.allRows.toSeq

      documentTags must haveTheSameElementsAs(sourceDocumentTags ++ cloneDocumentTags)
    }
    
    "don't try to clone if there are no tags" in new DocumentTagContext {
      DocumentTagCloner.dbClone(sourceDocumentSetId, cloneDocumentSetId, Map()) must not(throwA[Exception])
      
    }
    
  }
  step(shutdownDb)
}