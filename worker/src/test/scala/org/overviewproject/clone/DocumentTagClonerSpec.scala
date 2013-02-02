package org.overviewproject.clone

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.{ Document, DocumentTag }
import org.overviewproject.tree.orm.DocumentType._
import persistence.{ Schema, Tag }
import org.squeryl.KeyedEntity

class DocumentTagClonerSpec extends DbSpecification {

  step(setupDb)

  "DocumentTagCloner" should {

    trait DocumentTagContext extends DbTestContext {
      var sourceDocumentSetId: Long = _
      var cloneDocumentSetId: Long = _
      var sourceDocumentTags: Seq[DocumentTag] = _
      var cloneDocumentTags: Seq[DocumentTag] = _
      
      var documentMapping: Map[Long, Long] = _
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

        val sourceDocuments = Seq.tabulate(10)(i => Document(CsvImportDocument, sourceDocumentSetId, text = Some("text-" + i)))
        val sourceTags = Seq.tabulate(10)(i => Tag(sourceDocumentSetId, "tag-i"))
        val cloneDocuments = sourceDocuments.map(_.copy(documentSetId = cloneDocumentSetId))
        val cloneTags = sourceTags.map(_.copy(documentSetId = cloneDocumentSetId))

        sourceDocumentTags = createDocumentTags(sourceDocuments, sourceTags)
        cloneDocumentTags = createDocumentTags(cloneDocuments, cloneTags)

        documentMapping = createMapping(sourceDocuments, cloneDocuments)
        tagMapping = createMapping(sourceTags, cloneTags)
        
        Schema.documentTags.insert(sourceDocumentTags)
      }

    }

    "clone DocumentTags" in new DocumentTagContext {
      DocumentTagCloner.clone(documentMapping, tagMapping)

      val documentTags = Schema.documentTags.allRows.toSeq

      documentTags must haveTheSameElementsAs(sourceDocumentTags ++ cloneDocumentTags)
    }
  }
  step(shutdownDb)
}