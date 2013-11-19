/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.parrot.server

import collection.mutable
import com.twitter.finagle.Service
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.util.{IgnorantTrustManager, IgnorantHostnameVerifier}
import com.twitter.util._
import javax.net.ssl.{TrustManager, SSLContext, HttpsURLConnection}
import util.Random

trait ParrotTransport[Req <: ParrotRequest, Rep] extends Service[Req, Rep] {
  val log = Logger.get(getClass.getName)
  private[this] val handlers = new mutable.ListBuffer[Try[Rep] => Unit]()

  override def apply(request: Req): Future[Rep] =
    sendRequest(request) respond { k =>
      log.debug("Response: " + k.toString)
      handlers foreach { _(k) }
    }

  def sendRequest(request: Req): Future[Rep]

  def createService(config: ParrotServerConfig[Req, Rep]) = new ParrotService[Req, Rep](config)

  def shutdown() { }

  def stats(response: Rep): Seq[String] = Nil

  def respond(f: Try[Rep] => Unit) {
    handlers += f
  }

  def start(config: ParrotServerConfig[Req, Rep]) {
    // Works around change in Java 6u22 that would otherwise prevent setting some http headers
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true")

    // Let's just trust everything, otherwise self-signed testing servers will barf.
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCertificates(), new java.security.SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier(new IgnorantHostnameVerifier())
  }

  private[this] def trustAllCertificates(): Array[TrustManager] = Array(new IgnorantTrustManager)

  // Random IP generation support
  private val rnd = new Random(Time.now.inMillis)
  private def octet = rnd.nextInt(254) + 1
  def randomIp = "%d.%d.%d.%d".format(octet, octet, octet, octet)
}
