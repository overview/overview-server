package com.overviewdocs.searchindex

import java.io.IOException
import java.nio.file.{Files,FileVisitResult,Path,SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.ByteBuffer
import org.apache.lucene.analysis.icu.ICUNormalizer2Filter
import org.apache.lucene.analysis.{Analyzer,TokenStream}
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.{Document=>LuceneDocument,Field=>LuceneField,FieldType,NumericDocValuesField,StringField,TextField}
import org.apache.lucene.index.{DirectoryReader,FieldInfo,IndexOptions,IndexReader,IndexWriter,IndexWriterConfig,LeafReaderContext,NumericDocValues,Term}
import org.apache.lucene.search.{ConstantScoreQuery,IndexSearcher,SimpleCollector,Query=>LuceneQuery}
import org.apache.lucene.store.{Directory,FSDirectory}
import org.apache.lucene.util.{BytesRef,QueryBuilder}
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery
import scala.collection.mutable.{ArrayBuffer,BitSet}
import scala.collection.JavaConversions

import com.overviewdocs.query.{Field,Query}
import com.overviewdocs.models.Document

/** A Lucene index for a given document set.
  *
  * These operations may block on I/O. Such is Lucene.
  *
  * These operations are not concurrent: do not call more than one of these
  * methods at a time. They're synchronized, just in case.
  */
class DocumentSetLuceneIndex(val directory: Directory) { 
  private val analyzer = new DocumentSetLuceneIndex.OverviewAnalyzer
  private val queryBuilder = new QueryBuilder(analyzer)

  private val indexWriterConfig = new IndexWriterConfig(analyzer)
    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
    .setRAMBufferSizeMB(48) // "sweet spot" in https://issues.apache.org/jira/browse/LUCENE-843
    .setCommitOnClose(true) // for when we're evicted from MruLuceneIndexCache
  private val indexWriter = new IndexWriter(directory, indexWriterConfig)
  private var _directoryReader: Option[DirectoryReader] = None
  private var _indexReader: Option[IndexReader] = None
  private var _indexSearcher: Option[IndexSearcher] = None
  private def indexReader = _directoryReader.getOrElse(DirectoryReader.open(directory))
  private def indexSearcher = _indexSearcher.getOrElse(new IndexSearcher(indexReader))

  private def textFieldType(willHighlight: Boolean): FieldType = {
    val ret = new FieldType
    ret.setTokenized(true)
    if (willHighlight) {
      ret.setStored(true)
      ret.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
    } else {
      ret.setStored(false)
      ret.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) // phrase query needs positions
    }
    ret
  }

  def addDocuments(documents: Iterable[Document]): Unit = synchronized {
    addDocumentsWithoutFsync(documents)
    commit
  }

  def addDocumentsWithoutFsync(documents: Iterable[Document]): Unit = synchronized {
    // Instantiate objects once: saves GC. As per
    // https://wiki.apache.org/lucene-java/ImproveIndexingSpeed
    val idField = new NumericDocValuesField("id", 0L)
    val titleField = new LuceneField("title", "", textFieldType(false))
    val textField = new LuceneField("text", "", textFieldType(true))
    val luceneDocument = new LuceneDocument()
    luceneDocument.add(idField)
    luceneDocument.add(titleField)
    luceneDocument.add(textField)

    for (document <- documents) {
      idField.setLongValue(document.id)
      titleField.setStringValue(document.title)
      textField.setStringValue(document.text)
      indexWriter.addDocument(luceneDocument)
    }
  }

  def close: Unit = synchronized {
    indexWriter.close
  }

  def delete: Unit = synchronized {
    refresh
    indexReader.close
    indexWriter.close

    directory match {
      case fsDirectory: FSDirectory => deletePathRecursively(fsDirectory.getDirectory)
    }
  }

  private def deletePathRecursively(root: Path): Unit = {
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(dir: Path, e: IOException) = {
        if (e != null) throw e
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }
    })
  }

  private def searchForLuceneIds(documentIds: Seq[Long]): Seq[(Long,Int)] = {
    val query: LuceneQuery = {
      import org.apache.lucene.document.NumericDocValuesField
      import org.apache.lucene.search.{BooleanClause,BooleanQuery,ConstantScoreQuery}

      val builder = new BooleanQuery.Builder()
      for (documentId <- documentIds) {
        builder.add(NumericDocValuesField.newExactQuery("id", documentId), BooleanClause.Occur.SHOULD)
      }
      new ConstantScoreQuery(builder.build)
    }

    val ret = new ArrayBuffer[(Long,Int)](documentIds.length)

    indexSearcher.search(query, new SimpleCollector {
      var leafDocBase: Int = _
      var leafDocumentIds: NumericDocValues = _

      override def needsScores: Boolean = false

      override def doSetNextReader(context: LeafReaderContext): Unit = {
        leafDocBase = context.docBase
        leafDocumentIds = context.reader.getNumericDocValues("id")
      }

      override def collect(docId: Int): Unit = {
        val overviewId = leafDocumentIds.get(docId)
        val luceneId = leafDocBase + docId
        ret.+=((overviewId, luceneId))
      }
    })

    ret
  }

  private def queryToLuceneHighlightQuery(query: Query): LuceneQuery = {
    val luceneQuery = queryToLuceneQuery(query)
    indexSearcher.rewrite(luceneQuery)
  }

  def searchForIds(q: Query): Seq[Long] = synchronized {
    val ret = ArrayBuffer.empty[Long]

    indexSearcher.search(queryToLuceneQuery(q), new SimpleCollector {
      var leafDocumentIds: NumericDocValues = _

      override def needsScores: Boolean = false

      override def doSetNextReader(context: LeafReaderContext): Unit = {
        leafDocumentIds = context.reader.getNumericDocValues("id")
        assert(leafDocumentIds != null)
      }

      override def collect(docId: Int): Unit = {
        val documentId = leafDocumentIds.get(docId)
        ret.+=(documentId)
      }
    })

    ret
  }

  def highlight(documentId: Long, query: Query): Seq[Utf16Highlight] = synchronized {
    val highlighter = new LuceneSingleDocumentHighlighter(indexSearcher, analyzer)
    val luceneQuery = queryToLuceneHighlightQuery(query)

    searchForLuceneIds(Seq(documentId))
      .flatMap({ case (_, luceneId) =>
        highlighter.highlightFieldAsHighlights("text", luceneQuery, luceneId)
      })
  }

  def highlights(documentIds: Seq[Long], query: Query): Map[Long, Seq[Utf16Snippet]] = synchronized {
    val highlighter = new LuceneMultiDocumentHighlighter(indexSearcher, analyzer)
    val luceneQuery = queryToLuceneHighlightQuery(query)

    searchForLuceneIds(documentIds)
      .map({ case (overviewId, luceneId) =>
        val utf16Snippets: Array[Utf16Snippet] = highlighter.highlightFieldAsSnippets("text", luceneQuery, luceneId)
        (overviewId, utf16Snippets.toSeq)
      })
      .filter(_._2.nonEmpty)
      .toMap
  }

  private def commit: Unit = {
    indexWriter.commit

    _directoryReader match {
      case None => {}
      case Some(reader) => {
        Option(DirectoryReader.openIfChanged(reader)) match {
          case None => {}
          case Some(reader2) => {
            reader.close

            _indexReader = Some(reader2)
            _indexSearcher = None
          }
        }
      }
    }
  }

  def refresh: Unit = synchronized {
    commit
  }

  private def fieldToLuceneQuery(field: Field, buildLuceneQuery: String => LuceneQuery): LuceneQuery = {
    import org.apache.lucene.search

    field match {
      case Field.All => new search.BooleanQuery.Builder()
        .add(buildLuceneQuery("text"), search.BooleanClause.Occur.SHOULD)
        .add(buildLuceneQuery("title"), search.BooleanClause.Occur.SHOULD)
        .build
      case Field.Text => buildLuceneQuery("text")
      case Field.Title => buildLuceneQuery("title")
    }
  }

  private def queryToLuceneQuery(query: Query): LuceneQuery = {
    new ConstantScoreQuery(queryToLuceneQueryInner(query))
  }

  private def createPrefixQuery(field: String, prefix: String): LuceneQuery = {
    val ret = new MultiPhrasePrefixQuery // TODO set maxExpansions

    // run the analyzer
    val tokenStream = analyzer.tokenStream(field, prefix)
    val termAttribute = tokenStream.getAttribute(classOf[CharTermAttribute])

    val terms = ArrayBuffer.empty[Term]

    tokenStream.reset
    while (tokenStream.incrementToken) {
      val token = termAttribute.toString
      ret.add(new Term(field, token))
    }
    tokenStream.end
    tokenStream.close

    ret
  }

  private def queryToLuceneQueryInner(q: Query): LuceneQuery = {
    import com.overviewdocs.query
    import org.apache.lucene.search

    q match {
      case query.AllQuery => new search.MatchAllDocsQuery
      case query.AndQuery(p1, p2) => {
        new search.BooleanQuery.Builder()
          .add(queryToLuceneQueryInner(p1), search.BooleanClause.Occur.FILTER)
          .add(queryToLuceneQueryInner(p2), search.BooleanClause.Occur.FILTER)
          .build
      }
      case query.OrQuery(p1, p2) => {
        new search.BooleanQuery.Builder()
          .add(queryToLuceneQueryInner(p1), search.BooleanClause.Occur.SHOULD)
          .add(queryToLuceneQueryInner(p2), search.BooleanClause.Occur.SHOULD)
          .build
      }
      case query.NotQuery(p) => {
        new search.BooleanQuery.Builder()
          .add(queryToLuceneQueryInner(p), search.BooleanClause.Occur.MUST_NOT)
          .build
      }
      case query.PhraseQuery(field, phrase) => {
        fieldToLuceneQuery(field, fieldName => queryBuilder.createPhraseQuery(fieldName, phrase))
      }
      case query.PrefixQuery(field, prefix) => {
        fieldToLuceneQuery(field, fieldName => createPrefixQuery(fieldName, prefix))
      }
      case query.ProximityQuery(field, phrase, slop) => {
        fieldToLuceneQuery(field, fieldName => queryBuilder.createPhraseQuery(fieldName, phrase, slop))
      }
      case query.FuzzyTermQuery(field, term, fuzziness) => {
        fieldToLuceneQuery(field, { fieldName =>
          val luceneTerm = new Term(fieldName, term)
          // TODO set maxExpansions
          fuzziness match {
            case None => new search.FuzzyQuery(luceneTerm)
            case Some(fuzz) => new search.FuzzyQuery(luceneTerm, fuzz)
          }
        })
      }
    }
  }
}

object DocumentSetLuceneIndex {
  class OverviewAnalyzer extends Analyzer {
    override def createComponents(field: String): Analyzer.TokenStreamComponents = {
      val tokenizer = new StandardTokenizer
      val normalized = new ICUNormalizer2Filter(tokenizer)
      new Analyzer.TokenStreamComponents(tokenizer, normalized)
    }

    override def normalize(field: String, in: TokenStream): TokenStream = {
      new ICUNormalizer2Filter(in)
    }
  }
}
