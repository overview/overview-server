package org.overviewproject.http

import org.overviewproject.clustering.DCDocumentAtURL

class DocumentCloudBulkHttpRetriever(asyncHttpRetriever: AsyncHttpRetriever) extends BulkHttpRetriever[DCDocumentAtURL](asyncHttpRetriever) {

}