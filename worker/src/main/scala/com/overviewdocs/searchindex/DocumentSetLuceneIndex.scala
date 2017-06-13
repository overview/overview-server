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
import org.apache.lucene.search.{BooleanClause,BooleanQuery,ConstantScoreQuery,FuzzyQuery,IndexSearcher,MatchAllDocsQuery,MatchNoDocsQuery,MultiPhraseQuery,MultiTermQuery,PhraseQuery,PrefixQuery,SimpleCollector,Query=>LuceneQuery}
import org.apache.lucene.store.{Directory,FSDirectory}
import org.apache.lucene.util.{BytesRef,QueryBuilder}
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery
import play.api.libs.json.{JsNull,JsString}
import scala.collection.{mutable,immutable}

import com.overviewdocs.query.{Field,FieldInSearchIndex,Query}
import com.overviewdocs.models.{Document,DocumentIdSet}

/** A Lucene index for a given document set.
  *
  * These operations may block on I/O. Such is Lucene.
  *
  * These operations are not concurrent: do not call more than one of these
  * methods at a time. They're synchronized, just in case.
  */
class DocumentSetLuceneIndex(val documentSetId: Long, val directory: Directory, val maxExpansionsPerTerm: Int = 1000, val maxNMetadataFields: Int = 100) {
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
  private[searchindex] lazy val metadataFields: mutable.Map[String,LuceneField] = readMetadataFields

  private def readMetadataFields: mutable.Map[String,LuceneField] = {
    val ret = mutable.Map.empty[String,LuceneField]
    import scala.collection.JavaConversions

    val leaves = try {
      JavaConversions.iterableAsScalaIterable(indexReader.leaves)
    } catch {
      case e: org.apache.lucene.index.IndexNotFoundException => {
        // The index has never been saved. That means no metadata.
        return ret
      }
    }

    for (leaf <- leaves) {
      for (fieldInfo <- JavaConversions.iterableAsScalaIterable(leaf.reader.getFieldInfos)) {
        if (fieldInfo.name.startsWith("metadata:")) {
          val fieldName = fieldInfo.name.substring("metadata:".size)
          // Assume all metadata fields were stored with the same options
          ret.put(fieldName, buildMetadataLuceneField(fieldName))
        }
      }
    }

    ret
  }

  def nMetadataFields: Int = metadataFields.size

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

  private def buildMetadataLuceneField(fieldName: String): LuceneField = {
    new LuceneField("metadata:" + fieldName, "", textFieldType(false))
  }

  private def getMetadataFieldIfAllowed(name: String): Option[LuceneField] = {
    metadataFields.get(name) match {
      case Some(field) => Some(field)
      case None if metadataFields.size < maxNMetadataFields => {
        val field = buildMetadataLuceneField(name)
        metadataFields.put(name, field)
        Some(field)
      }
      case _ => None
    }
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

    for (document <- documents) {
      luceneDocument.clear

      // Index id,title,text: same on every doc
      idField.setLongValue(document.id)
      titleField.setStringValue(document.title)
      textField.setStringValue(document.text)
      luceneDocument.add(idField)
      luceneDocument.add(titleField)
      luceneDocument.add(textField)

      // Index metadata: different on every doc
      for ((key, value) <- document.metadataJson.value) {
        getMetadataFieldIfAllowed(key) match {
          case Some(metadataField) => {
            val stringValue: String = value match {
              case JsString(s) => s
              case JsNull => ""
              case _ => value.toString
            }
            metadataField.setStringValue(stringValue)
            luceneDocument.add(metadataField)
          }
          case None => // too many fields! Skip this one
        }
      }

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

    val ret = new mutable.ArrayBuffer[(Long,Int)](documentIds.length)

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

  def searchForIds(query: Query): SearchResult = synchronized {
    val (luceneQuery, warnings) = queryToLuceneQuery(query)
    val lowerIds = new mutable.BitSet

    indexSearcher.search(luceneQuery, new SimpleCollector {
      var leafDocumentIds: NumericDocValues = _

      override def needsScores: Boolean = false

      override def doSetNextReader(context: LeafReaderContext): Unit = {
        leafDocumentIds = context.reader.getNumericDocValues("id")
        assert(leafDocumentIds != null)
      }

      override def collect(docId: Int): Unit = {
        val documentId = leafDocumentIds.get(docId)
        assert(documentId >> 32 == documentSetId)
        lowerIds.add(documentId.toInt) // truncate to lower 32 bits
      }
    })

    SearchResult(
      DocumentIdSet(documentSetId.toInt, immutable.BitSet.fromBitMaskNoCopy(lowerIds.toBitMask)),
      warnings
    )
  }

  def highlight(documentId: Long, query: Query): Seq[Utf16Highlight] = synchronized {
    val (luceneQuery, warnings) = queryToLuceneQuery(query)

    val highlighter = new LuceneSingleDocumentHighlighter(indexSearcher, analyzer)
    searchForLuceneIds(Seq(documentId))
      .flatMap({ case (_, luceneId) =>
        highlighter.highlightFieldAsHighlights("text", luceneQuery, luceneId)
      })
  }

  def highlights(documentIds: Seq[Long], query: Query): Map[Long,Seq[Utf16Snippet]] = synchronized {
    val (luceneQuery, warnings) = queryToLuceneQuery(query)

    val highlighter = new LuceneMultiDocumentHighlighter(indexSearcher, analyzer)

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

  private def fieldToLuceneQuery(field: Field, buildLuceneQuery: FieldInSearchIndex => (LuceneQuery, List[SearchWarning])): (LuceneQuery, List[SearchWarning]) = {
    field match {
      case Field.All => {
        val (q1, w1) = buildLuceneQuery(Field.Text)
        val (q2, w2) = buildLuceneQuery(Field.Title)
        val innerLuceneQuery = new BooleanQuery.Builder()
          .add(q1, BooleanClause.Occur.SHOULD)
          .add(q2, BooleanClause.Occur.SHOULD)
          .build
        (innerLuceneQuery, w1 ::: w2)
      }
      case fieldInSearchIndex: FieldInSearchIndex => buildLuceneQuery(fieldInSearchIndex)
    }
  }

  private def queryToLuceneQuery(query: Query): (LuceneQuery, List[SearchWarning]) = {
    val (innerQuery, warnings) = queryToLuceneQueryInner(query)
    val luceneQuery = new ConstantScoreQuery(innerQuery)

    (luceneQuery, warnings)
  }

  private def buildPrefixQuery(field: FieldInSearchIndex, prefix: String): (LuceneQuery, List[SearchWarning]) = {
    val fieldName = fieldToName(field)
    val terms: Seq[String] = phraseToTermStrings(field, prefix)

    assert(terms.nonEmpty)

    if (terms.size == 1) {
      (new PrefixQuery(new Term(fieldName, terms.head)), Nil)
    } else {
      val builder = new MultiPhraseQuery.Builder
      terms.take(terms.length - 1).foreach { term =>
        builder.add(new Term(fieldName, term))
      }

      val (expandedTerms, warnings) = expandPrefixTerm(field, terms.last)

      var luceneQuery: LuceneQuery = null

      if (expandedTerms.isEmpty) {
        // Copied from ElasticSearch:
        // if the terms does not exist we could return a MatchNoDocsQuery but this would break the unified highlighter
        // which rewrites query with an empty reader.
        luceneQuery = new BooleanQuery.Builder()
          .add(builder.build, BooleanClause.Occur.MUST)
          .add(new MatchNoDocsQuery(fieldName + ":" + terms.last + "* does not appear in the search index"), BooleanClause.Occur.MUST)
          .build
      } else {
        builder.add(expandedTerms)
        luceneQuery = builder.build
      }

      (luceneQuery, warnings)
    }
  }

  /** Returns all the terms in the Lucene indexed field that start with the
    * given String.
    */
  private def expandPrefixTerm(field: FieldInSearchIndex, prefix: String): (Array[Term], List[SearchWarning]) = {
    val fieldName = fieldToName(field)
    val prefixBytes = new Term(fieldName, prefix).bytes()
    val ret = mutable.ArrayBuffer.empty[Term]

    // The rest of this method comes from ElasticSearch
    // org/elasticsearch/common/lucene/search/MultiPhrasePrefixQuery.java with
    // slight Scala modifications. It's Apache-licensed; see ElasticSearch
    // project for copyright details.
    import scala.collection.JavaConversions
    import org.apache.lucene.index.TermsEnum
    import org.apache.lucene.util.StringHelper;

    // SlowCompositeReaderWrapper could be used... but this would merge all terms from each segment into one terms
    // instance, which is very expensive. Therefore I think it is better to iterate over each leaf individually.
    val leaves = indexReader.leaves
    for (leaf <- JavaConversions.collectionAsScalaIterable(leaves)) {
      val _terms = leaf.reader.terms(fieldName)
      if (_terms != null) {
        val termsEnum = _terms.iterator()
        val seekStatus = termsEnum.seekCeil(prefixBytes)

        if (TermsEnum.SeekStatus.END != seekStatus) {
          var term = termsEnum.term()
          while (term != null && StringHelper.startsWith(term, prefixBytes)) {
            if (ret.size >= maxExpansionsPerTerm) {
              return (ret.toArray, SearchWarning.TooManyExpansions(field, prefix + "*", maxExpansionsPerTerm) :: Nil)
            }

            ret.append(new Term(fieldName, BytesRef.deepCopyOf(term)))

            term = termsEnum.next()
          }
        }
      }
    }

    (ret.toArray, Nil)
  }

  private def buildBooleanQuery(p1: Query, p2: Query, occur: BooleanClause.Occur): (LuceneQuery, List[SearchWarning]) = {
    val (q1, warnings1) = queryToLuceneQueryInner(p1)
    val (q2, warnings2) = queryToLuceneQueryInner(p2)

    val ret = new BooleanQuery.Builder()
      .add(q1, occur)
      .add(q2, occur)
      .build

    (ret, warnings1 ::: warnings2)
  }

  private def fieldToName(field: FieldInSearchIndex): String = field match {
    case Field.Text => "text"
    case Field.Title => "title"
    case Field.Metadata(name) => s"metadata:${name}"
  }

  private def buildFuzzyQuery(field: FieldInSearchIndex, term: String, fuzz: Option[Int]) = {
    val luceneTerm = new Term(fieldToName(field), term)
    val luceneQuery = fuzz match {
      case None => new FuzzyQuery(luceneTerm)
      case Some(fuzz) => new FuzzyQuery(luceneTerm, fuzz)
    }

    luceneQuery.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_REWRITE)

    (luceneQuery, Nil)
  }

  private def phraseToTermStrings(field: FieldInSearchIndex, phrase: String): Seq[String] = {
    val tokenStream = analyzer.tokenStream(fieldToName(field), phrase)
    val termAttribute = tokenStream.getAttribute(classOf[CharTermAttribute])

    val terms = mutable.ArrayBuffer.empty[String]

    tokenStream.reset
    while (tokenStream.incrementToken) {
      terms.append(termAttribute.toString)
    }
    tokenStream.end
    tokenStream.close

    terms
  }

  private def buildPhraseQuery(field: FieldInSearchIndex, phrase: String, slop: Int) = {
    val terms = phraseToTermStrings(field, phrase)

    (new PhraseQuery(slop, fieldToName(field), terms: _*), Nil)
  }

  private def queryToLuceneQueryInner(q: Query): (LuceneQuery, List[SearchWarning]) = {
    import com.overviewdocs.query

    q match {
      case query.AllQuery => (new MatchAllDocsQuery, Nil)
      case query.AndQuery(p1, p2) => buildBooleanQuery(p1, p2, BooleanClause.Occur.FILTER)
      case query.OrQuery(p1, p2) => buildBooleanQuery(p1, p2, BooleanClause.Occur.SHOULD)
      case query.NotQuery(p) => {
        val (innerQuery, warnings) = queryToLuceneQueryInner(p)
        val luceneNotQuery = new BooleanQuery.Builder()
          .add(innerQuery, BooleanClause.Occur.MUST_NOT)
          .build
        (luceneNotQuery, warnings)
      }
      case query.PhraseQuery(field, phrase) => {
        fieldToLuceneQuery(field, f => buildPhraseQuery(f, phrase, 0))
      }
      case query.PrefixQuery(field, prefix) => {
        fieldToLuceneQuery(field, f => buildPrefixQuery(f, prefix))
      }
      case query.ProximityQuery(field, phrase, slop) => {
        fieldToLuceneQuery(field, f => buildPhraseQuery(f, phrase, slop))
      }
      case query.FuzzyTermQuery(field, term, fuzziness) => {
        fieldToLuceneQuery(field, f => buildFuzzyQuery(f, term, fuzziness))
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
