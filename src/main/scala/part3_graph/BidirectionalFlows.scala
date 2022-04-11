package part3_graph

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlows extends App {
  implicit val system = ActorSystem("BidirectionalFlows")
  implicit val materializer = ActorMaterializer()

  /*
  Example: cryptography
   */
  def encrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)

  // bidiFlow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3)))
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("akka","is","awesome","testing","bidirectional","flows")
  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))
  val cryptoBidiGraph = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape = builder.add(unencryptedSource)
      val encryptedSourceShape = builder.add(encryptedSource)
      val encryptedSinkShape = builder.add(Sink.foreach[String](s => println(s"Encrypted: $s")))
      val unencryptedSinkShape = builder.add(Sink.foreach[String](s => println(s"Unencrypted: $s")))
      val bidiShape = builder.add(bidiCryptoStaticGraph)

      unencryptedSourceShape ~> bidiShape.in1 ; bidiShape.out1 ~> encryptedSinkShape
      unencryptedSinkShape   <~ bidiShape.out2; bidiShape.in2  <~ encryptedSourceShape

      ClosedShape
    }
  }
  cryptoBidiGraph.run()
  /*
  - encrypting / decrypting
  - encoding / decoding
  - serializing / deserializing
   */
}
