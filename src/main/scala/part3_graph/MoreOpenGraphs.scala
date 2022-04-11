package part3_graph

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

import java.util.Date

object MoreOpenGraphs extends App {
  implicit val system = ActorSystem("MoreOpenGraphs")
  implicit val materializer = ActorMaterializer()

  /*
  Max3 operator
  - 3 inputs of type int
  - the maximum of the 3
   */
  // step 1 - boilerplate
  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // step 2 - define auxiliary SHAPES
    val max1 = builder.add(ZipWith[Int,Int,Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int,Int,Int]((a, b) => Math.max(a, b)))

    // step 3
    max1.out ~> max2.in0

    //step 4
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach(println)

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape
      source2 ~> max3Shape ~> maxSink
      source3 ~> max3Shape

      ClosedShape
    }
  )
//  max3RunnableGraph.run()

  /*
  Non-uniform fan out shape

  Processing bank transactions
  Txn suspicious if amount > 10,000

  Stream components for txns
  - output1: let the transaction go thru unmodified
  - output2: suspicious txn ids
   */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("5273890572", "Paul", "Jim", 100, new Date),
    Transaction("3578902532", "Daniel", "Jim", 100000, new Date),
    Transaction("5489036033", "Jim", "Alice", 7000, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }
  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val suspiciousShape = builder.add(suspiciousTxnStaticGraph)

      transactionSource ~> suspiciousShape.in
      suspiciousShape.out0 ~> bankProcessor
      suspiciousShape.out1 ~> suspiciousAnalysisService

      ClosedShape
    }
  )
  suspiciousTxnRunnableGraph.run()
}
