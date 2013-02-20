/*
 * Copyright (c) 2011-2012 Doug Tangren
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 *   "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.overviewproject.sbt.assetbundler

import org.mozilla.javascript.{ Context, Function, JavaScriptException, NativeObject }
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * A Scala / Rhino Coffeescript compiler.
 * @author daggerrz
 * @author doug (to a lesser degree)
 */
object CoffeeScriptCompiler {
  val coffeeScriptSrc = "coffee-script.js"
  val utf8 = Charset.forName("utf-8")

  /** compiler arguments in addition to `bare` */
  def args: Map[String, Any] = Map.empty[String, Any]

  override def toString = "%s(%s)" format(getClass.getSimpleName, coffeeScriptSrc)

  /**
   * Compiles a string of Coffeescript code to Javascript.
   *
   * @param code the Coffeescript source code
   * @param bare whether the Coffeescript compiler should run in "bare" mode
   * @return Either a compilation error description or
   *   the compiled Javascript code
   */
  def compile(code: String, bare: Boolean): Either[String, String] =
    withContext { ctx =>
      val coffee = scope.get("CoffeeScript", scope).asInstanceOf[NativeObject]
      val compileFunc = coffee.get("compile", scope).asInstanceOf[Function]
      val opts = ctx.evaluateString(scope, jsArgs(bare), null, 1, null)
      try {
        Right(compileFunc.call(
          ctx, scope, coffee, Array(code, opts)).asInstanceOf[String])
      } catch {
        case e : JavaScriptException =>
          Left(e.getValue.toString)
      }
    }

  lazy val scope = withContext { ctx =>
    val scope = ctx.initStandardObjects()
    ctx.evaluateReader(
      scope,
      new InputStreamReader(
        getClass().getResourceAsStream("/%s" format coffeeScriptSrc), utf8
      ), coffeeScriptSrc, 1, null
    )

    scope
  }

  private def withContext[T](f: Context => T): T =
    try {
      val ctx = Context.enter()
      // Do not compile to byte code (max 64kb methods)
      ctx.setOptimizationLevel(-1)
      f(ctx)
    } finally {
      Context.exit()
    }

  private def jsArgs(bare: Boolean) =
    ((List.empty[String] /: (Map("bare" -> bare) ++ args)) {
      (a,e) => e match {
        case (k, v) =>
          "%s:%s".format(k, v match {
            case s: String => "'%s'" format s
            case lit => lit
          }) :: a
        }
    }).mkString("({",",","});")
}
