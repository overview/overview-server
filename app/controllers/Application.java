package controllers;

import play.*;
import play.data.*;
import play.mvc.*;

import views.html.*;

import models.*;


public class Application extends Controller {

    static Form<DocumentSet> documentSetForm = form(DocumentSet.class);

    public static Result index() {
    	return ok(index.render("The Overview Project"));
    }

    public static Result showDocumentSets() {
    	return ok(documentSets.render(DocumentSet.all(), documentSetForm));
    }

    public static Result newDocumentSet() {
    	Form<DocumentSet> filledForm = documentSetForm.bindFromRequest();
    	DocumentSet.create(filledForm.get());

    	return redirect(routes.Application.showDocumentSets());
    }
    
}