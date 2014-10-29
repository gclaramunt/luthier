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
package base

import typelist._
import akka.actor._
import scala.concurrent._, scala.util._, scala.concurrent.duration._
import scala.reflect.{ ClassTag, classTag }
import shapeless._

/**
 * TODO: document
 */
class VM private[VM] (val appContext: AppContext) {

  val log = akka.event.Logging(appContext.actorSystem, this)(new akka.event.LogSource[VM] {
      def genString(f) = f.appContext.name + ":VM"
    })
  //Supervisor of all instantiated endpoints
  private[VM] class SetupEndpoint(val actorFactory: () => Actor, val actorName: String)
  private[VM] class KillEndpoint(val ref: ActorRef)
  private[VM] val endpointsSupervisor = try {
    appContext.actorSystem.actorOf(Props(new Actor {
          log.info(s"Supervisor endpoint created for actor system ${appContext.actorSystem}")
          def receive = {
            case s: SetupEndpoint =>
              val ref = context.actorOf(Props(s.actorFactory()), s.actorName)
              log.debug(s"$ref registered by $sender")
              sender ! ref
            case k: KillEndpoint =>
              sender ! context.stop(k.ref)
              log.debug(s"${k.ref} stopped by $sender")
          }
        }), "VM")
  } catch {
    case e: InvalidActorNameException =>
      log.info(s"Supervisor endpoint found already created in actor system ${appContext.actorSystem}")
      //the supervisor is already there, so just reference it
      //we already know that the actor is there, so ther is no real need to wait here.
      Await.result(appContext.actorSystem.actorSelection("user/VM").resolveOne(10.millis), 20.millis)
  }

  //////////////////////////////////////////////////////////////////////
  // Inbound endpoints
  //////////////////////////////////////////////////////////////////////

  trait VmInboundEndpointBase extends InboundEndpoint {
    /**
     * Subpath of the actor that represents this endpoint.
     */
    def actorPath: String

    @volatile private[this] var _endpointActor: ActorRef = _
    def endpointActor = _endpointActor

    protected def newReceiverActor: Actor

    def start() {
      val creationFuture = akka.pattern.ask(endpointsSupervisor).ask(new SetupEndpoint(() => newReceiverActor, actorPath))(500.millis).
      map(e => e.asInstanceOf[ActorRef])(appContext.actorSystem.dispatcher)
      _endpointActor = Await.result(creationFuture, 500.millis)
    }
    def dispose() {
      val killFuture = akka.pattern.ask(endpointsSupervisor).ask(new KillEndpoint(_endpointActor))(500.millis)
      Await.result(killFuture, 500.millis)
    }
  }

  case class ClassTags[H <: HList](val classTags: HList) {
    val possibleClasses = classTags.toList.asInstanceOf[List[ClassTag[_]]].map(_.runtimeClass)
    def checkInstance(i: Any): Boolean = {
      possibleClasses.exists(_.isInstance(i))
    }
  }
  object ClassTags {
    implicit def classTagsForHNil: ClassTags[HNil] = ClassTags[HNil](HNil)
    implicit def classTagsForTail[H, T <: HList](implicit ct: ClassTag[H], tail: ClassTags[T]): ClassTags[H :: T] = ClassTags[H :: T](ct :: tail.classTags)
  }


  class VMSourceEndpoint[ReqTypes <: HList] private[VM] (val flow: Flow, val actorPath: String)(implicit classTags: ClassTags[ReqTypes]) extends BaseSource with VmInboundEndpointBase {
    type PossiblePayloads = ReqTypes
    def newReceiverActor = new Actor {
      def receive = {
        case msg =>
          if (classTags checkInstance msg) {
            messageArrived(newReceviedMessage(msg)(null))
          } else {
            log.error(s"Received on $actorPath a message of type ${msg.getClass.getName} but this actor is typed as ${classTags.possibleClasses.mkString(", ")}. Message ignored")
          }
      }
    }
  }
  case class SourceEndpointFactory[ReqTypes <: HList] private[VM] (actorPath: String)(implicit classTags: ClassTags[ReqTypes]) extends EndpointFactory[VMSourceEndpoint[ReqTypes]] {
    def apply(f) = new VMSourceEndpoint[ReqTypes](f, actorPath)
  }

  def source[ReqTypes <: HList](actorPath: String)(implicit classTags: ClassTags[ReqTypes]) = SourceEndpointFactory[ReqTypes](actorPath)


  class VMResponsibleEndpoint[ReqTypes <: HList, ResponseType <: HList] private[VM] (
    val flow: Flow, val actorPath: String)(implicit classTags: ClassTags[ReqTypes]) extends Responsible with VmInboundEndpointBase {
    type PossiblePayloads = ReqTypes
    type SupportedResponseTypes = ResponseType
    def newReceiverActor = new Actor {
      def receive = {
        case msg =>
          if (classTags checkInstance msg) {
            requestArrived(newReceviedMessage(msg)(null), self, sender)
          } else {
            log.error(s"Received on $actorPath a message of type ${msg.getClass.getName} but this actor is typed as ${classTags.possibleClasses.mkString(", ")}. Message ignored")
          }
      }
    }

    private def requestArrived(m: Message[Payload], self: ActorRef, requestor: ActorRef) {
      implicit val ec = appContext.actorSystem.dispatcher
      val f = onRequestHandler(m)
      f.onComplete {
        case Success(msg) => requestor.tell(msg.payload.unsafeValue, self)
        case Failure(err) => requestor.tell(err, self)
      }
      f onFailure { case ex => log.error(ex, "Error on flow " + flow) }
    }
  }
  case class ResponsibleEndpointFactory[ReqTypes <: HList, ResponseType <: HList] private[VM] (actorPath: String)(implicit classTags: ClassTags[ReqTypes]) extends EndpointFactory[VMResponsibleEndpoint[ReqTypes, ResponseType]] {
    override def apply(f) = new VMResponsibleEndpoint[ReqTypes, ResponseType](f, actorPath)
  }

  def responsible[ReqTypes <: HList, ResponseType <: HList](actorPath: String)(implicit classTags: ClassTags[ReqTypes]) = ResponsibleEndpointFactory[ReqTypes, ResponseType](actorPath)

  //////////////////////////////////////////////////////////////////////
  // Outbound endpoints
  //////////////////////////////////////////////////////////////////////

  class VmOutboundEndpoint[OutSupportedTypes <: HList, ExpectedResponse] private[VM] (
    val flow: Flow, val actorPath: String) extends Pushable with Askable {
    override type SupportedTypes = OutSupportedTypes
    override type Response = ExpectedResponse
    protected def destActor = appContext.actorSystem.actorSelection(actorPath)
    override def start() {}
    override def dispose() {}
    override def push[Payload: SupportedType](msg: Message[Payload]): Future[Unit] = Future.successful(destActor.tell(msg.payload, null))
    override def ask[Payload: SupportedType](msg: Message[Payload], timeOut: FiniteDuration): Future[Message[Response]] = {
      akka.pattern.ask(destActor).?(msg.payload)(timeOut).map(r => msg.map(_ => r.asInstanceOf[Response]))(appContext.actorSystem.dispatcher)
    }
  }
  case class VmOutboundEndpointFactory[OutSupportedTypes <: HList, ExpectedResponse] private[VM] (
    actorPath: String) extends EndpointFactory[VmOutboundEndpoint[OutSupportedTypes, ExpectedResponse]] {
    override def apply(f) = new VmOutboundEndpoint[OutSupportedTypes, ExpectedResponse](f, actorPath)
  }

  def sink[Out](actorPath: String) = VmOutboundEndpointFactory[Out :: HNil, Any](actorPath)
  def ref[Out, ExpectedResponse](actorPath: String) = VmOutboundEndpointFactory[Out :: HNil, ExpectedResponse](actorPath)
}
object VM {
  def forAppContext(ac: AppContext) = new VM(ac)
}