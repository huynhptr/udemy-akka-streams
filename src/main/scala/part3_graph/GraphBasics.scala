package part3_graph

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

object GraphBasics extends App {
  implicit val system = ActorSystem("GraphBasics")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map {x => x + 1} //hard computation
  val multiplier = Flow[Int].map {x => x * 10} //hard computation
  val output = Sink.foreach[(Int,Int)](println)

  //step 1 - setting up fundamental for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._ // bring some nice operators into scope

      //step 2 - add the necessary components of this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int,Int]) // fan-in operator

      //step 3 - tying up the components
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output

      // step 4 - return a closed shape
      ClosedShape
      //shape
    }// graph
  )// runnable graph

//  graph.run() // run the graph and materialize it

  /*
  Ex1: Feed a source into 2 sinks at the same time
   */
  val firstSink = Sink.foreach[Int](x=>println(s"Sink1: $x"))
  val secondSink = Sink.foreach[Int](x=>println(s"Sink2: $x"))
  val graph1 = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      val broadcast = builder.add(Broadcast[Int](2))
      import GraphDSL.Implicits._
      input ~> broadcast ~> firstSink //allocate ports in order
               broadcast ~> secondSink
      ClosedShape
    }
  }
//  graph1.run()

  /*
  Ex2: Graph with Merge and Balance
   */
  import scala.concurrent.duration._
  val fastSource = Source(1 to 1000).throttle(1, 0.05 second)
  val slowSource = Source(1001 to 2000).throttle(1, 0.1 second)
  val sink1 = Sink.foreach[Int](x=>println(s"sink1: $x"))
  val sink2 = Sink.foreach[Int](x=>println(s"sink2: $x"))



  val graph2 = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge
                             balance ~> sink2

      ClosedShape
    }
  }
  graph2.run()

}
