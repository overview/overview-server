package com.overviewdocs.sort

import akka.stream.scaladsl.Source
import com.ibm.icu.text.{Collator,RawCollationKey}
import com.fasterxml.jackson.core.{JsonFactory,JsonToken,SerializableString}
import com.fasterxml.jackson.core.io.SerializedString
import java.util.{Arrays,Locale}
import scala.collection.immutable
import scala.concurrent.Future

import com.overviewdocs.database.Database

class DocumentSource(
  val database: Database,
  val nDocumentsPerFetch: Int = 100
) {
  import database.api._

  def recordSourceByMetadata(documentSetId: Long, fieldName: String): Future[RecordSource] = {
    import database.api._
    import database.executionContext

    for {
      ids <- database.option(sql"SELECT sorted_document_ids FROM document_set WHERE id = ${documentSetId}".as[Seq[Long]])
    } yield {
      val ids32Bit: Array[Int] = ids.get.toArray.map(_.toInt)

      val initialPositions = new Array[Int](ids32Bit.max + 1)
      ids32Bit.zipWithIndex.foreach { case (id, position) => initialPositions(id) = position }

      // TODO use document language for collation? Or take locale as a
      // parameter? We should consider what happens with multi-language
      // docsets.
      val collator = Collator.getInstance(new Locale("en-US"))
      val rawCollationKey = new RawCollationKey
      val serializedFieldName = new SerializedString(fieldName)
      def rowToRecord(id32Bit: Int, jsonText: String): Record = {
        val value = DocumentSource.jsonFieldString(jsonText, serializedFieldName)
        collator.getRawCollationKey(value, rawCollationKey)
        val collationKeyBytes: Array[Byte] = Arrays.copyOf(rawCollationKey.bytes, rawCollationKey.size)
        Record(id32Bit, initialPositions(id32Bit), collationKeyBytes)
      }

      val groups: Iterator[Array[Int]] = ids32Bit.grouped(nDocumentsPerFetch)

      val source: Source[Record, _] = Source.fromIterator(() => groups)
        .flatMapConcat { someIds32Bit =>
          val someIds = someIds32Bit.map(id32Bit => (documentSetId << 32) | id32Bit)
          val futurePageSource: Future[Source[Record, akka.NotUsed]] = for {
            rows: Vector[(Int,String)] <- database.run(sql"""
              SELECT id::BIT(32)::INT4, COALESCE(metadata_json_text, '{}')
              FROM document
              WHERE document_set_id = ${documentSetId}
                AND id IN (#${someIds.mkString(",")})
            """.as[(Int,String)])
          } yield {
            val records = rows.map(tuple => rowToRecord(tuple._1, tuple._2))
            Source(records)
          }
          Source.fromFutureSource[Record, akka.NotUsed](futurePageSource)
        }

      RecordSource(ids32Bit.size, source)
    }
  }
}

object DocumentSource {
  private val jsonFactory = new JsonFactory
  private def jsonFieldString(jsonText: String, serializedFieldName: SerializableString): String = {
    val parser = jsonFactory.createParser(jsonText)

    assert(parser.nextToken == JsonToken.START_OBJECT)
    while (!parser.isClosed) {
      if (parser.nextFieldName(serializedFieldName)) {
        parser.nextToken
        val value: String = parser.getValueAsString("")
        parser.close
        return value
      } else {
        parser.nextToken    // even if we hit EOF
        parser.skipChildren // even if we hit EOF
      }
    }

    return ""
  }
}
