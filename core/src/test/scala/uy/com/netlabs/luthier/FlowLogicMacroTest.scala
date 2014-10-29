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

import typelist._
import testing.TestUtils
import endpoint.base.VM
import scala.concurrent._
import shapeless._

/**
 * This test only needs to compile, not to be run.
 */
class FlowLogicMacroTest extends Flows {
  val appContext = AppContext.build("test")

  val Vm = VM.forAppContext(appContext)
  new Flow("test")(Vm.responsible[Int :: HNil, String :: <::<[Throwable] :: HNil]("test")) {
    logic { req =>
      implicitly[rootEndpoint.SupportedResponseTypes =:= InboundEndpointTpe#SupportedResponseTypes]
      implicitly[rootEndpoint.SupportedResponseTypes =:= (String :: <::<[Throwable] :: HNil)]
      implicitly[InboundEndpointTpe#SupportedResponseTypes =:= (String :: <::<[Throwable] :: HNil)]
      implicitly[Supported[String, InboundEndpointTpe#SupportedResponseTypes]]
      val v = OneOf[String, this.type]("")
      (0: Any) match {
        case 0 => Future.successful(OneOf("")).recover { case e => OneOf(e) }.map(v => req.map(_ => v))
        case 1 => req.map(_ => "Hi")
        case 2 => req.map(_ => new Exception("Boom"))
        case _ =>
          TestUtils.assertTypeErrorEquals("4: LogicResult")("""Invalid response type for the flow.
  Found: Int(4)
  Expected a Message[T] or a Future[Message[T]] where T can be any of [
    String
    <::<[Throwable]
  ]""")
          TestUtils.assertTypeErrorEquals("Future.successful(()): LogicResult")("""Invalid message type for the flow.
 Found: Unit
 Expected a Message[T] where T can be any of [
    String
    <::<[Throwable]
  ]""")
          TestUtils.assertTypeErrorEquals("Future.successful(()).map(_ => req.map(_ => 8)): LogicResult")("""Int is not contained in [
  String,
  <::<[Throwable]
]""")
          val f = Future.successful(3f)
          TestUtils.assertTypeErrorEquals("f: LogicResult")("""Invalid response type for the flow.
  Found: scala.concurrent.Future[Float]
  Expected a Message[T] or a Future[Message[T]] where T can be any of [
    String
    <::<[Throwable]
  ]""")
          Future.successful(req.map(_ => new Exception("Boom ")))
      }
    }
  }
}
