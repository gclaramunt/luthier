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

import scala.language._
import scala.collection.mutable._

/**
 * Value implicitly available during a run.
 * Useful to store run temporal information as well as a message factory.
 */
trait FlowRun[+FlowType <: Flow] extends MessageFactory {

  /**
   * Message that started the run, i.e.: the message declared in logic
   */
  val rootMessage: RootMessage[FlowType]
  /**
   * The flow to which this run belongs
   */
  val flow: FlowType
  /**
   * A run context to put anything you like.
   */
  val context: Map[Any, Any] = scala.collection.concurrent.TrieMap.empty

  private[this] var afterFlowResponseReactions = Set.empty[() => Unit]
  /**
   * Registers a thunk to be executed after the the response of the flow is obtained.
   * That is, if the flow is oneway, when the last line of code is executed, if the
   * flow is request response, when the response is obtained (if the response is
   * wrapped in a future, then it awaits for the future to complete).
   */
  def afterFlowResponse(code: => Unit) {
    afterFlowResponseReactions += (() => code)
  }
  private[luthier] def flowResponseCompleted() {
    for (r <- afterFlowResponseReactions) r()
  }
  private[this] var afterFlowRunReactions = Set.empty[() => Unit]
  /**
   * Registers a thunk to be executed after all of the code written in the flow
   * completes. That includes every future mapping that was done and would hence
   * run in the flow.
   * 
   * An easy way to think of this method is to register a thunk to be run when all
   * visible code in the logic has been run.
   */
  def afterWholeFlowRun(code: => Unit) {
    afterFlowRunReactions += (() => code)
  }
  private[luthier] def wholeFlowRunCompleted() {
    for (r <- afterFlowRunReactions) r()
  }

  private[this] var lastProducedMessage0: Message[_] = _
  private[this] var lastReceivedMessage0: Message[_] = rootMessage
  def lastProducedMessage: Message[_] = lastProducedMessage0
  def lastReceivedMessage: Message[_] = lastReceivedMessage0
  def messageSent[P](m: Message[P]) = {
    lastProducedMessage0 = m
    m
  }
  def createReceivedMessage[P](payload: P) = {
    val res = lastProducedMessage.map(_ => payload)
    lastProducedMessage0 = res //FIXME: not sure this makes much sense...
    res
  }

  def apply[P](payload: P) = {
    val res = lastReceivedMessage0.map(_ => payload)
    lastProducedMessage0 = res
    res
  }
}
object FlowRun {
  type Any = FlowRun[_ <: Flow]
  
  implicit def workerActorsExecutionContextFromFlowRun(implicit fr: FlowRun.Any): scala.concurrent.ExecutionContext =
    fr.flow.workerActorsExecutionContext(fr.rootMessage.asInstanceOf[RootMessage[fr.flow.type]])
}
