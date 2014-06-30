package org.overviewproject.clone

import anorm._

object TreeCloner extends InDatabaseCloner {

  override def cloneQuery: SqlQuery =
    SQL("""
        INSERT INTO tree (id, document_set_id, job_id, title, document_count, lang, supplied_stop_words,
          important_words, description, created_at)
          SELECT ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id), {cloneDocumentSetId}, 0, title, document_count, lang, supplied_stop_words, 
            important_words, description, created_at
          FROM tree WHERE document_set_id = {sourceDocumentSetId}
        """)
}
