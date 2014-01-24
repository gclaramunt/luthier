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
package endpoint

import scala.concurrent._, stm.{atomic, Ref, InTxn}

import typelist._

/**
 * A FeedableEndpoint is a special channel that receives input programatically using
 * a FeedableEndpoint.Requestor. One feeding input using the requestor you get back a
 * future with the result of the flow.
 */
object FeedableEndpoint {
  class Requestor[Req, Resp <: TypeList]() {
    private val pendingRequests = Ref(Vector.empty[(Req, Promise[OneOf[_, Resp]])])
    private val connectedEndpoint = Ref.make[FeedableEndpointBase[Req, Resp]]()
    def request(r: Req): Future[OneOf[_, Resp]] = {
      val res = Promise[OneOf[_, Resp]]()
      atomic { implicit tx =>
        pendingRequests.transform(_ :+ r->res)
        tryFeedEndpoint()
      }
      res.future
    }
    private[FeedableEndpoint] def connect(e: FeedableEndpointBase[Req, Resp]): Unit = {
      atomic { implicit tx =>
        connectedEndpoint.set(e)
        tryFeedEndpoint()
      }
    }
    private def tryFeedEndpoint()(implicit tx: InTxn) {
      val ep = connectedEndpoint()
      if (ep != null) {
        val reqs = pendingRequests()
        reqs foreach { case (req, promise) => promise completeWith ep.feed(req) }
        pendingRequests() = Vector.empty
      }
    }
  }

  private[FeedableEndpoint] trait FeedableEndpointBase[Req, Resp <: TypeList] {
    def requestor: Requestor[Req, Resp]
    def feed(r: Req): Future[OneOf[_, Resp]]
  }

  class FeedableSource[Req](val flow: Flow, val requestor: Requestor[Req, Unit :: TypeNil]) extends FeedableEndpointBase[Req, Unit :: TypeNil] with Source {
    type Payload = Req
    def feed(r: Req): Future[OneOf[_, Unit :: TypeNil]] = {
      flow.runFlow(r.asInstanceOf[flow.InboundEndpointTpe#Payload]).map(_ =>
        new OneOf[Unit, Unit :: TypeNil](()))(flow.rawWorkerActorsExecutionContext)
    }
    def dispose(): Unit = {}
    def start(): Unit = requestor.connect(this)
  }
  class FeedableResponsible[Req, Resp <: TypeList](val flow: Flow, val requestor: Requestor[Req, Resp]) extends FeedableEndpointBase[Req, Resp] with Responsible {
    type Payload = Req
    type SupportedResponseTypes = Resp
    def feed(r: Req): Future[OneOf[_, Resp]] = {
      onRequestHandler(newReceviedMessage(r)).map(_.payload)(flow.rawWorkerActorsExecutionContext)
    }
    def dispose(): Unit = {}
    def start(): Unit = requestor.connect(this)
  }

  case class source[Req](requestor: Requestor[Req, Unit :: TypeNil]) extends EndpointFactory[FeedableSource[Req]] {
    def apply(f) = new FeedableSource[Req](f, requestor)
  }
  case class responsible[Req, Resp <: TypeList](requestor: Requestor[Req, Resp]) extends EndpointFactory[FeedableResponsible[Req, Resp]] {
    def apply(f) = new FeedableResponsible[Req, Resp](f, requestor)
  }
}