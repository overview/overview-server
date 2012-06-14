package controllers

class DocumentSetIndexer(var text:String) {
	def munge:String = {
	  return text + " told you so!"
	}
}