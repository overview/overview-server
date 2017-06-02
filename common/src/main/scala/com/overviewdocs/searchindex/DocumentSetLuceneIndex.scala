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
    ret.setStored(true)
    ret.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
    ret
  }

  private def documentToLuceneDocument(document: Document): LuceneDocument = {
    val ret = new LuceneDocument()
    ret.add(new NumericDocValuesField("id", document.id))
    ret.add(new LuceneField("title", document.title, textFieldType))
    ret.add(new LuceneField("text", document.text, textFieldType))
    ret.add(new LuceneField("_all", document.title + "\n\n\n" + document.text, textFieldType))
    ret
  }

  def addDocuments(documents: Iterable[Document]): Unit = {
    val luceneDocuments = documents.map(d => documentToLuceneDocument(d))
    indexWriter.addDocuments(JavaConversions.asJavaIterable(luceneDocuments))
    commit
  }

  def close: Unit = indexWriter.close

  def delete: Unit = {
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

  def searchForLuceneIds(documentIds: Seq[Long]): Seq[Int] = {
    val query: LuceneQuery = {
      import org.apache.lucene.document.NumericDocValuesField
      import org.apache.lucene.search.{BooleanClause,BooleanQuery,ConstantScoreQuery}

      val builder = new BooleanQuery.Builder()
      for (documentId <- documentIds) {
        builder.add(NumericDocValuesField.newExactQuery("id", documentId), BooleanClause.Occur.SHOULD)
      }
      new ConstantScoreQuery(builder.build)
    }

    val ret = ArrayBuffer.empty[Int]

    indexSearcher.search(query, new SimpleCollector {
      var leafDocBase: Int = _

      override def needsScores: Boolean = false

      override def doSetNextReader(context: LeafReaderContext): Unit = {
        leafDocBase = context.docBase
      }

      override def collect(docId: Int): Unit = {
        ret.+=(leafDocBase + docId)
      }
    })

    ret
  }

  def searchForIds(q: Query): Seq[Long] = {
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

  def highlight(documentId: Long, query: Query): Seq[Highlight] = {
    val highlighter = new LuceneSingleDocumentHighlighter(indexSearcher, analyzer)

    val luceneQuery = indexSearcher.rewrite(queryToLuceneQuery(query))

    searchForLuceneIds(Seq(documentId))
      .flatMap(docId => highlighter.highlightFieldAsHighlights("text", luceneQuery, docId))
  }

  def highlights(documentIds: Seq[Long], q: Query): Map[Long, Seq[Snippet]] = Map() // TODO

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

  def refresh: Unit = commit

  private def fieldToLuceneField(f: Field): String = {
    f match {
      case Field.All => "_all"
      case Field.Text => "text"
      case Field.Title => "title"
    }
  }

  private def queryToLuceneQuery(query: Query): LuceneQuery = {
    new ConstantScoreQuery(queryToLuceneQueryInner(query))
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
