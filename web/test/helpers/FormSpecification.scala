package test.helpers

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form

class FormSpecification extends Specification {
  trait BaseScope[A] extends Scope {
    def form: Form[A]
  }

  /** Tests that a form applies properly.
    *
    * Usage:
    *
    *   trait SomethingApplyScope extends ApplyScope[Something] {
    *     override def form = SomethingForm()
    *   }
    *
    *   "form should parse properly" in new SomethingApplyScope {
    *     override def args = Map("a" -> "b", "c" -> "d")
    *     value must beSome(Something("b", "d"))
    *   }
    *
    *   "form should give errors properly" in new SomethingApplyScope {
    *     override def args = Map("a" -> "b", "b" -> "a")
    *     error("key") must beSome(FormError("key", "message", Seq[]))
    *     globalError must beSome(FormError("?", "message", Seq[2]))
    *   }
    * }
    */
  trait ApplyScope[A] extends BaseScope[A] {
    def args : Map[String,String]
    def bind = form.bind(args)
    def value = bind.value
    def error(key: String) = bind.error(key)
    def globalError = bind.globalError
    def errors = bind.errors
  }
}
