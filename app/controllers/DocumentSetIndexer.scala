package controllers

import play._
import play.libs.WS
import play.libs.F.Promise
import play.libs.F.Function;
import play.libs.Json

import models._

import scala.collection.JavaConversions._


class DocumentSetIndexer(var documentSet:DocumentSet) {

  // Query documentCloud and create one document object for each doc returned by the query
  def createDocuments() = {
    val queryString = documentSet.query
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json"
    
    // Query DocumentCloud for all docs matching query string
    val DCcall = WS.url(documentCloudQuery).setQueryParameter("q", queryString).get();
    val response = DCcall.get();  // blocks until result comes back. but does it tie up the thread? not sure, async() may be better
  
    // Iterate over returned docs, each one described by a block of JSON
    val documentReferences  = response.asJson().get("documents")
    for (document <- documentReferences) { 
      
      var title = document.get("title").toString()
      title = title.replace("\"", "")
      
      var canonicalUrl = document.get("canonical_url").toString()
  
      canonicalUrl = canonicalUrl.replace("\"", "")
      var textUrl = document.get("resources").get("text").toString()
      textUrl = textUrl.substring(1,textUrl.length-1) // remove quotes
                      
      val newDoc = new Document(documentSet, title, textUrl, canonicalUrl)
      newDoc.save()
      documentSet.documents.add(newDoc)
    }
   
    documentSet.update();     
  }
  
  def indexDocuments() = {
    for (document <- documentSet.documents) {
      val textUrl = document.textUrl;
      println(textUrl)
     
      val response = WS.url(textUrl).get().get().getBody()
      println(response + "\n------------------------")
    }
  }
}