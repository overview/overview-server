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
    		filledForm.get().save();

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

//		System.out.println(document.title);
		
    	return ok(viewDocument.render(document));
    }
        
    public static Result viewDocumentSet(Long id) {
    	final DocumentSet documentSet = DocumentSet.find.byId(id);
    	String queryString = documentSet.query;
    	String documentCloudQuery = "http://www.documentcloud.org/api/search.json";
    	
    	Promise<WS.Response> DCcall= WS.url(documentCloudQuery).setQueryParameter("q", queryString).get();
    	WS.Response response = DCcall.get();	// blocks until result comes back. but does it tie up the thread? not sure, async() may be better
    	
		JsonNode documentReferences = response.asJson().get("documents");
		
		Document documentToView = null;
		
		for (JsonNode document : documentReferences) {
			String documentId = document.get("id").toString();
			String title = document.get("title").toString();
			title = title.replace("\"", "");
			String canonicalUrl = document.get("canonical_url").toString();
			canonicalUrl = canonicalUrl.replace("\"", "");
			
			DocumentSetIndexer titleObj = new DocumentSetIndexer(title);
			title = titleObj.munge();
			
			//System.out.println(title);
			
			Document newDoc = new Document(documentSet, title, canonicalUrl, canonicalUrl);
			newDoc.save();
			
			if (documentToView == null) documentToView = newDoc;
			
			documentSet.documents.add(newDoc);
		}
		
		documentSet.update();
		return redirect(routes.Application.viewDocument(documentToView.id));
    }
}
