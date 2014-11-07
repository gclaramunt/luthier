/**
 * Copyright (c) 2013, Netlabs S.R.L. <contacto@netlabs.com.uy>
 * All rights reserved.
 *
 * This software is dual licensed as GPLv2: http://gnu.org/licenses/gpl-2.0.html,
 * and as the following 3-clause BSD license. In other words you must comply to
 * either of them to enjoy the permissions they grant over this software.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name "netlabs" nor the names of its contributors may be
 *       used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NETLABS S.R.L.  BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package uy.com.netlabs.luthier

import language.experimental._
import scala.reflect.macros._

object MacroTest {

  def showMeTheTypesImpl[T: c.WeakTypeTag](c: scala.reflect.macros.Context): c.Expr[Unit] = {
    val tt = implicitly[c.WeakTypeTag[T]]
    println("Type is = " + tt)
    c.universe.reify(())
  }

  def showMeTheTypes[T]: Unit = macro showMeTheTypesImpl[T]

  def showMeTheCodeImpl(ctx: scala.reflect.macros.Context)(c: ctx.Expr[Any]): ctx.Expr[Unit] = {
    println(ctx.universe.showRaw(c))
    ctx.universe.reify(())
  }

  def showMeTheCode(c: Any): Unit = macro showMeTheCodeImpl

  def normalMethod = "normalMethod"

  def replaceMeWithMethodImpl(c: scala.reflect.macros.Context)() = {
    import c.universe._
    val normalDefTree = reify(normalMethod).tree
    println(showRaw(normalDefTree) + " - " + normalDefTree.pos)
//    val tree = DefDef(Modifiers(), newTermName("myMethod"), List(), List(), TypeTree(), Literal(Constant("hi there")))
//    val e = c.Expr(tree)
//    e
    val t = c.parse("def myMethod = \"Hi there\"")
    t.symbol = c.macroApplication.symbol
//    for (n <- t) n.symbol = c.macroApplication.symbol
    c.Expr(t)
  }

  def replaceMeWithMethod() = macro replaceMeWithMethodImpl


//  class TransformerList[SupportedTypes] {
//    def transform[T](t: T)(implicit acceptance: T <:< SupportedTypes) {
//
//    }
//  }
//
//  val t = new TransformerList[String with Serializable with Int]
//  t.transform("lalal")
}