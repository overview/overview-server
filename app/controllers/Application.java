package controllers;

import org.codehaus.jackson.JsonNode;

import play.*;
import play.data.*;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.*;

import views.html.*;

import models.*;

import java.util.*;

public class Application extends Controller {
    static Form<DocumentSet> documentSetForm = form(DocumentSet.class);

    public static Result index() {
    	return ok(index.render("The Overview Project"));
    }

    public static Result showDocumentSets() {
    	return ok(documentSets.render(DocumentSet.find.all(), documentSetForm));
    }

    public static Result newDocumentSet() {
    	Form<DocumentSet> filledForm = documentSetForm.bindFromRequest();
    	
    	if(filledForm.hasErrors()) {
    		return badRequest(
    				views.html.documentSets.render(DocumentSet.find.all(), filledForm)
    				);
    	}
    	else {
    	  	final DocumentSet documentSet = filledForm.get();
    		documentSet.save();

    		// Now retrieve all the documents within this set, creating document objects
			DocumentSetIndexer indexer = new DocumentSetIndexer(documentSet);
			indexer.createDocuments();
			indexer.indexDocuments();
			    		
    		return redirect(routes.Application.showDocumentSets());
    	}
    }
    
    public static Result deleteDocumentSet(Long id) {
    	DocumentSet documentSet = DocumentSet.find.ref(id);
    	documentSet.delete();
    	
    	return redirect(routes.Application.showDocumentSets());
    }

    public static Result viewDocument(Long id) {
    	Document document = Document.find.byId(id);

    	return ok(viewDocument.render(document));
    }
        
    public static Result viewDocumentSet(Long id) {
  
	   	final DocumentSet documentSet = DocumentSet.find.byId(id);
	   	
		// Just view the first doc for now
	   	Document doc = documentSet.documents.iterator().next();
		return redirect(routes.Application.viewDocument(doc.id));
    }
}
