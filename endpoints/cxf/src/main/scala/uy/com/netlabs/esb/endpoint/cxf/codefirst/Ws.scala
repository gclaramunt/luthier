package uy.com.netlabs.esb
package endpoint
package cxf.codefirst

import language._

import uy.com.netlabs.esb.reflect.util.MethodRef
import typelist._
import scala.concurrent._, util.Duration, util.duration._
import scala.util._
import java.util.concurrent.Executors
import org.apache.cxf.endpoint.Server

object Ws {

  class WsResponsible[I, PL, R] private[Ws] (f: Flow,
                                             val sei: Sei[I],
                                             val methodRef: MethodRef[I, PL, R],
                                             val maxResponseTimeout: Duration,
                                             val ioWorkers: Int) extends base.BaseResponsible {
    type SupportedResponseTypes = R :: TypeNil
    type Payload = PL

    implicit val flow = f

    private var server: Server = _
    def start() {
      server = sei.srvFactory.create
    }
    def dispose() {
      server.destroy()
      ioExecutor.shutdown()
    }

    private val ioExecutor = Executors.newFixedThreadPool(ioWorkers)
    implicit val ioExecutionContext = ExecutionContext.fromExecutor(ioExecutor)

    sei.InterfaceImplementor.methodImplementors += methodRef.method -> { args =>
      val payload = args match { //map from array to tuple
        case arr if arr.length == 0 => ()
        case arr if arr.length == 1 => arr(0)
        case arr if arr.length == 2 => (arr(0), arr(1))
        case arr if arr.length == 3 => (arr(0), arr(1), arr(2))
        case arr if arr.length == 4 => (arr(0), arr(1), arr(2), arr(3))
        case arr if arr.length == 5 => (arr(0), arr(1), arr(2), arr(3), arr(4))
        case arr if arr.length == 6 => (arr(0), arr(1), arr(2), arr(3), arr(4), arr(5))
        case arr if arr.length == 7 => (arr(0), arr(1), arr(2), arr(3), arr(4), arr(5), arr(6))
        case arr if arr.length == 8 => (arr(0), arr(1), arr(2), arr(3), arr(4), arr(5), arr(6), arr(7))
        case arr if arr.length == 9 => (arr(0), arr(1), arr(2), arr(3), arr(4), arr(5), arr(6), arr(7), arr(8))
        case arr if arr.length == 10 => (arr(0), arr(1), arr(2), arr(3), arr(4), arr(5), arr(6), arr(7), arr(8), arr(9))
      }
      val m = messageFactory(payload.asInstanceOf[PL])
      val resultPromise = Promise[Message[OneOf[_, SupportedResponseTypes]]]()
      requestArrived(m, resultPromise.complete)
      Await.result(resultPromise.future, maxResponseTimeout).payload.value //re-throw exception in the try on purpose
    }
  }

  private case class EF[I, PL, R](s: Sei[I], maxResponseTimeout: Duration, ioWorkers: Int)(f: I => MethodRef[I, PL, R]) extends EndpointFactory[WsResponsible[I, PL, R]] {
    def apply(flow: Flow) = new WsResponsible[I, PL, R](flow, s, f(null.asInstanceOf[I]), maxResponseTimeout, ioWorkers)
  }
  def apply[I, PL, R](s: Sei[I],
                      maxResponseTimeout: Duration = 30.seconds,
                      ioWorkers: Int = 4)(f: I => MethodRef[I, PL, R]): EndpointFactory[Responsible { //weird type declaration used instead of just WsResponsible[I, PL, R] because of bug in scalac
    type SupportedResponseTypes = WsResponsible[I, PL, R]#SupportedResponseTypes
    type Payload = WsResponsible[I, PL, R]#Payload
  }] = EF(s, maxResponseTimeout, ioWorkers)(f)
}