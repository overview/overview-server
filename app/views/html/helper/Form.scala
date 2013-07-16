package views.html.helper

import play.api.data.{ Field, FormError }
import play.api.templates.Html
import play.api.i18n.{ Lang, Messages }
import play.api.mvc.RequestHeader
import scala.xml.{ UnprefixedAttribute, MetaData }

object Form {
  import scala.language.implicitConversions
  
  private case class RichField(field: Field) {
    private val ConstraintRequiredKey = "constraint.required"
    private val EmailKey = "constraint.email"

    def isRequired: Boolean = {
      field.constraints.find({ case (key, args) => key == ConstraintRequiredKey || key == EmailKey }).isDefined
    }
  }
  private implicit def fieldToRichField(field: Field) = RichField(field)

  def errors(errors: Seq[FormError])(implicit lang: Lang): Html = {
    if (errors.length > 0) {
      Html(
        <div class="control-group error">
          <ul class="unstyled controls">
            {
              errors.map { error =>
                <li class="help-block error">{ Messages(error.message, error.args: _*) }</li>
              }
            }
          </ul>
        </div>.buildString(false))
    } else {
      Html("")
    }
  }

  def input(field: Field, options: Map[Symbol, String] = Map())(implicit lang: Lang): Html = {
    val fieldsetClassName = "control-group" + field.error.map({ (e: FormError) => " error" }).getOrElse("")
    val id = options.get('prefix).map(_.toString + "-").getOrElse("") + field.id
    val name = field.id
    val inputType = options.get('type).getOrElse("text")
    val required: Boolean = options.get('required).map(_ != "false").getOrElse(field.isRequired)
    val optionalHelpText: Option[String] = options.get('helpText)
    val optionalLabel: Option[String] = options.get('label)

    val attributes = options --
      Seq('prefix, 'required, 'type, 'helpText, 'label) ++
      (if (required) Seq('required -> "required") else Seq())

    Html(<fieldset class={ fieldsetClassName }>
           { optionalLabel.map(label => <label for={ id } class="control-label">{ label }</label>).getOrElse("") }
           <div class="controls">
             { (<input id={ id } type={ inputType } name={ name }/>) % attributes }
             {
               optionalHelpText.map({ helpText =>
                 <p class="help-block">{ helpText }</p>
               }).getOrElse("")
             }
             {
               field.errors.map { error =>
                 <p class="help-block">{ Messages(error.message, error.args: _*) }</p>
               }
             }
           </div>
         </fieldset>.buildString(false))
  }

  def translatedInput(field: Field, m: views.ScopedMessages, options: Map[Symbol, String] = Map())(implicit lang: Lang): Html =  {
    input(field, options ++ descriptionOptions(field, m))
  }

  def checkbox(field: Field, options: Map[Symbol, String] = Map())(implicit lang: Lang): Html = {
    val fieldsetClassName = "control-group" + field.error.map({ (e: FormError) => " error" }).getOrElse("")
    val id = options.get('prefix).map(_.toString + "-").getOrElse("") + field.id
    val name = field.id
    val required: Boolean = options.get('required).map(_ != "false").getOrElse(field.isRequired)
    val inputType = options.get('type).getOrElse("text")
    val optionalHelpText: Option[String] = options.get('helpText)
    val optionalLabel: Option[String] = options.get('label)

    val attributes = options --
      Seq('prefix, 'required, 'type, 'helpText, 'label) ++
      (if (required) Seq('required -> "required") else Seq())

    Html(<fieldset class={ fieldsetClassName }>
           <div class="controls">
             <label>
               { (<input id={ id } type={ inputType } name={ name }/>) % attributes }
               { optionalLabel.getOrElse("") }
             </label>
             {
               optionalHelpText.map({ helpText =>
                 <p class="help-block">{ helpText }</p>
               }).getOrElse("")
             }
           </div>
         </fieldset>.buildString(false))
  }

  def translatedCheckbox(field: Field, m: views.ScopedMessages, options: Map[Symbol, String] = Map())(implicit lang: Lang): Html = {
    checkbox(field, options ++ descriptionOptions(field, m))
  }

  def csrfToken()(implicit request: RequestHeader) : Html = {
    Html(<input
      type="hidden"
      name={play.filters.csrf.CSRF.Conf.TOKEN_NAME}
      value={play.filters.csrf.CSRF.getToken(request).getOrElse(throw new Exception("CSRF.getToken() failed")).value}
      />.buildString(false))
  }

  private def descriptionOptions(field: Field, m: views.ScopedMessages): Map[Symbol, String] = 
     Map('label -> "label", 'placeholder -> "placeholder", 'helpText -> "help").flatMap { 
      case (sym, prefix) =>  m.optional(prefix + "." + field.name).map(sym -> _) 
  }
    
  private implicit def mapToAttributes(in: Map[Symbol, String]): scala.xml.MetaData = {
    in.foldLeft[MetaData](scala.xml.Null)((next, keyval) => new UnprefixedAttribute(keyval._1.name, keyval._2, next))
  }

  def translatedSubmit(m: views.ScopedMessages, attributes: Map[Symbol, String] = Map())(implicit lang: Lang): Html = {
    Html(
      <fieldset class="form-actions">
        { (<input type="submit" class="btn btn-primary" value={ m("submit") }/>) % attributes }
      </fieldset>.buildString(false))
  }
}
