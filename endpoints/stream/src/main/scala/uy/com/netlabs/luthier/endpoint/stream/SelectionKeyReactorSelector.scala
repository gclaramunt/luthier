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
package uy.com.netlabs.luthier.endpoint.stream

import java.nio.channels._

trait SelectionKeyReactorSelector {
  val selector: Selector
  def mustStop: Boolean
  def loopExceptionHandler: (SelectionKey, Throwable) => Unit

  def selectionLoop() {
    while (!mustStop) {
      debug(Console.MAGENTA + "Selecting..." + Console.RESET)
      if (selector.select() > 0) {
        debug(Console.YELLOW + s"${selector.selectedKeys().size} keys selected" + Console.RESET)
        val sk = selector.selectedKeys().iterator()
        while (sk.hasNext()) {
          val k = sk.next()
          try {
            debug(Console.GREEN + "Processing " + keyDescr(k) + Console.RESET)
            k.attachment().asInstanceOf[SelectionKeyReactor].react(k)
            debug(Console.GREEN + "Done with " + keyDescr(k) + Console.RESET)
          } catch {case ex: Throwable => loopExceptionHandler(k, ex)}
          sk.remove()
        }
      }
    }
  }
}