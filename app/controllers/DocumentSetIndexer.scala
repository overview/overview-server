package controllers

import play.libs.WS;

class DocumentSetIndexer(var textUrl:String) {
	def munge() = {
	  
	  if (textUrl != null) {
	    textUrl = textUrl.substring(1,textUrl.length-1) // remove quotes
	    println(textUrl)
	    
		val response = WS.url(textUrl).get().get().getBody()
		println(response + "\n------------------------")
	  }
	}
}