package com.overviewdocs.searchindex

import org.apache.lucene.analysis.icu.ICUNormalizer2Filter
import org.apache.lucene.analysis.{Analyzer,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.{Document=>LuceneDocument,Field=>LuceneField,FieldType,StoredField,StringField,TextField}
import org.apache.lucene.index.{DirectoryReader,FieldInfo,IndexOptions,IndexReader,IndexWriter,IndexWriterConfig,StoredFieldVisitor,Term}
import org.apache.lucene.search.{IndexSearcher,SimpleCollector,Query=>LuceneQuery}
import org.apache.lucene.store.Directory
import org.apache.lucene.util.QueryBuilder
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
  * methods at a time.
  */
class DocumentSetLuceneIndex(val directory: Directory) { 
  private val analyzer = new DocumentSetLuceneIndex.OverviewAnalyzer
  private val queryBuilder = new QueryBuilder(analyzer)

  private val indexWriterConfig = new IndexWriterConfig(analyzer)
    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
  private val indexWriter = new IndexWriter(directory, indexWriterConfig)
  private var _directoryReader: Option[DirectoryReader] = None
  private var _indexReader: Option[IndexReader] = None
  private var _indexSearcher: Option[IndexSearcher] = None
  private def indexReader = _directoryReader.getOrElse(DirectoryReader.open(directory))
  private def indexSearcher = _indexSearcher.getOrElse(new IndexSearcher(indexReader))

  private val textFieldType: FieldType = {
    val ret = new FieldType
    ret.setTokenized(true)
    ret.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
    ret
  }

  private def documentToLuceneDocument(document: Document): LuceneDocument = {
    val ret = new LuceneDocument()
    ret.add(new StoredField("id", document.id))
    ret.add(new LuceneField("title", document.title, textFieldType))
    ret.add(new LuceneField("text", document.text, textFieldType))
    ret.add(new LuceneField("_all", document.title + "\n\n\n" + document.text, textFieldType))
    ret
  }

  def addDocuments(documents: Iterable[Document]): Unit = {
    val luceneDocuments = documents.map(d => documentToLuceneDocument(d))
    indexWriter.addDocuments(JavaConversions.asJavaIterable(luceneDocuments))
  }

  def close: Unit = indexWriter.close

  def delete: Unit = {
    refresh
    indexReader.close
    ???
  }

  def searchForIds(q: Query): Seq[Long] = {
    // 1. Find Lucene-internal IDs. (These are transient.)
    //
    // We collect _all_ the IDs. 10M documents means luceneIds has size 1.25MB
    val luceneIds = new BitSet(indexReader.maxDoc)
    var size = 0

    indexSearcher.search(queryToLuceneQuery(q), new SimpleCollector {
      override def needsScores: Boolean = false
      override def collect(luceneId: Int): Unit = {
        luceneIds += luceneId
        size += 1
      }
    })

    // 2. Read all documents to find Overview Document IDs
    //
    // We choose to use an Array because we know the size.
    //
    // In the degenerate case of every document matching, 10M documents gives
    // size 80MB. Let's hope it doesn't often come to that. (Memory won't be
    // as much of a problem as the I/O bottleneck.)
    val overviewIds = new Array[Long](size)
    var nWritten: Int = 0
    val accumulateOverviewIds = new StoredFieldVisitor {
      override def needsField(fieldInfo: FieldInfo) = {
        if (fieldInfo.name == "id") {
          StoredFieldVisitor.Status.YES
        } else {
          StoredFieldVisitor.Status.NO
        }
      }

      override def longField(fieldInfo: FieldInfo, value: Long): Unit = {
        overviewIds(nWritten) = value
        nWritten += 1
      }
    }

    val reader = indexReader
    luceneIds.foreach { luceneId =>
      reader.document(luceneId, accumulateOverviewIds)
    }

    assert(nWritten == size)

    overviewIds
  }

  def highlight(documentId: Long, q: Query): Seq[Highlight] = Seq() // TODO

  def highlights(documentIds: Seq[Long], q: Query): Map[Long, Seq[Snippet]] = Map() // TODO

  def refresh: Unit = {
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

  private def fieldToLuceneField(f: Field): String = {
    f match {
      case Field.All => "_all"
      case Field.Text => "text"
      case Field.Title => "title"
    }
  }

  private def queryToLuceneQuery(q: Query): LuceneQuery = {
    import com.overviewdocs.query
    import org.apache.lucene.search

    q match {
      case query.AllQuery => new search.MatchAllDocsQuery
      case query.AndQuery(p1, p2) => {
        new search.BooleanQuery.Builder()
          .add(queryToLuceneQuery(p1), search.BooleanClause.Occur.FILTER)
          .add(queryToLuceneQuery(p2), search.BooleanClause.Occur.FILTER)
          .build
      }
      case query.OrQuery(p1, p2) => {
        new search.BooleanQuery.Builder()
          .add(queryToLuceneQuery(p1), search.BooleanClause.Occur.SHOULD)
          .add(queryToLuceneQuery(p2), search.BooleanClause.Occur.SHOULD)
          .build
      }
      case query.NotQuery(p) => {
        new search.BooleanQuery.Builder()
          .add(queryToLuceneQuery(p), search.BooleanClause.Occur.MUST_NOT)
          .build
      }
      case query.PhraseQuery(field, phrase) => {
        queryBuilder.createPhraseQuery(fieldToLuceneField(field), phrase)
      }
      case query.PrefixQuery(field, prefix) => {
        val ret = new MultiPhrasePrefixQuery // TODO set maxExpansions

        // run the analyzer
        val tokenStream = analyzer.tokenStream(fieldToLuceneField(field), prefix)
        val termAttribute = tokenStream.getAttribute(classOf[CharTermAttribute])

        val luceneField = fieldToLuceneField(field)
        val terms = ArrayBuffer.empty[Term]

        tokenStream.reset
        while (tokenStream.incrementToken) {
          val token = termAttribute.toString
          ret.add(new Term(luceneField, token))
        }
        tokenStream.end
        tokenStream.close

        ret
      }
      case query.ProximityQuery(field, phrase, slop) => {
        queryBuilder.createPhraseQuery(fieldToLuceneField(field), phrase, slop)
      }
      case query.FuzzyTermQuery(field, term, fuzziness) => {
        val luceneTerm = new Term(fieldToLuceneField(field), term)
        // TODO set maxExpansions
        fuzziness match {
          case None => new search.FuzzyQuery(luceneTerm)
          case Some(fuzz) => new search.FuzzyQuery(luceneTerm, fuzz)
        }
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
