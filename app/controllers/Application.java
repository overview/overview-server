package controllers;

import play.data.*;

import play.mvc.*;

import views.html.*; // TODO: remove this (or, preferably, remove most of the Application class

import models.*;


public class Application extends Controller {
    static Form<models.DocumentSet> documentSetForm = form(models.DocumentSet.class);

    public static Result index() {
    	return ok(index.render("The Overview Project"));
    }

    public static Result showDocumentSets() {
    	return ok(documentSets.render(models.DocumentSet.find.all(), documentSetForm));
    }

    public static Result newDocumentSet() {
    	Form<models.DocumentSet> filledForm = documentSetForm.bindFromRequest();
    	
    	if(filledForm.hasErrors()) {
    		return badRequest(
    				views.html.documentSets.render(models.DocumentSet.find.all(), filledForm)
    				);
    	}
    	else {
    	  	final models.DocumentSet documentSet = filledForm.get();
    		documentSet.save();

    		// Now retrieve all the documents within this set, creating document objects
			DocumentSetIndexer indexer = new DocumentSetIndexer(documentSet);
			indexer.createDocuments();
			indexer.indexDocuments();
			    		
    		return redirect(routes.Application.showDocumentSets());
    	}
    }
    
    public static Result deleteDocumentSet(Long id) {
    	models.DocumentSet documentSet = models.DocumentSet.find.ref(id);
    	documentSet.delete();
    	
    	return redirect(routes.Application.showDocumentSets());
    }

    public static Result viewDocument(Long id) {
    	Document document = Document.find.byId(id);

    	return ok(viewDocument.render(document));
    }
        
    public static Result viewDocumentSet(Long id) {
  
	   	final models.DocumentSet documentSet = models.DocumentSet.find.byId(id);
	   	
		// Just view the first doc for now
	   	Document doc = documentSet.documents.iterator().next();
		return redirect(routes.Application.viewDocument(doc.id));
    }
    
    public static Result showJobs() {
    	return ok(viewJobs.render(DocumentSetCreationJob.find.all()));
    	
    }
}
