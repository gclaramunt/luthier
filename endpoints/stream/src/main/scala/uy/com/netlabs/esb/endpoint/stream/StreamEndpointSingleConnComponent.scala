package uy.com.netlabs.esb
package endpoint
package stream

import typelist._
import scala.concurrent._, duration._
import scala.util._

import java.nio._, channels._

/**
 * Abstraction over single connection endpoints, note that they can still
 * be used as source endpoints.
 */
protected trait StreamEndpointSingleConnComponent {

  trait ConnEndpoint[S, P, R, SPR <: TypeList] extends base.BaseSource with base.BaseResponsible with base.BaseSink with Askable {
    //abstracts
    val conn: java.nio.channels.Channel
    val reader: Consumer[S, R]
    val readBuffer: Int
    val ioWorkers: Int
    protected def processResponseFromRequestedMessage(m: Message[OneOf[_, SupportedResponseTypes]])
    /**
     * Since we don't know how to read data from the specific conn, implementors
     * must provide us with such function.
     */
    protected val readBytes: ByteBuffer => Int

    type Payload = R
    type SupportedTypes = SPR
    type Response = R

    @volatile
    private var stop = false
    protected val syncConsumer = Consumer.Synchronous(reader, readBuffer)
    def start() {
      val initializeInboundEndpoint = onEventHandler != null || onRequestHandler != null
      if (initializeInboundEndpoint) {
        Future {
          while (!stop) {
            val read = syncConsumer.consume(readBytes)
            if (read.isSuccess) {
              val in = newReceviedMessage(read.get)
              if (onEventHandler != null) onEventHandler(in)
              else onRequestHandler(in) onComplete {
                case Success(response) => processResponseFromRequestedMessage(response)
                case Failure(err) => log.error(err, s"Error processing request $in")
              }
            } else log.error(read.failed.get, s"Failure reading from socket $conn")
          }
        } onFailure {
          case ex =>
            log.error(ex, s"Stoping flow $flow because of error")
            flow.dispose()
        }
      }
    }
    def dispose {
      stop = true
      scala.util.Try(conn.close())
      ioProfile.dispose()
    }

    val ioProfile = base.IoProfile.threadPool(ioWorkers)
    protected def pushMessage[MT: SupportedType](msg): Unit = {
      processResponseFromRequestedMessage(msg.map(p => new OneOf(p)(null))) //bypass creation of a contained, since the SupportedType implicit gives us that guarantee already
    }

    def ask[MT: SupportedType](msg, timeOut): Future[Message[Response]] = Future {
      val stream = msg.payload.asInstanceOf[(Int, P)]._1
      pushMessage(msg)(null) //by pass the evidence..
      msg map (_ => syncConsumer.consume(readBytes).get)
    }(ioExecutionContext)
  }
}