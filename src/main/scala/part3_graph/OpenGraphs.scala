package part3_graph

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source}

object OpenGraphs extends App {
  implicit val system = ActorSystem("OpenGraphs")
  implicit val materializer = ActorMaterializer()

  /*
  A composite source that concatenates 2 sources
  - emits all elements from the the 1st source
  - then all elements from the 2nd source
   */
  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph = Source.fromGraph {
    GraphDSL.create() {implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - declaring components
      val concat = builder.add(Concat[Int](2))

      // step 3 - tying them together
      firstSource ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    }
  }
//  sourceGraph.to(Sink.foreach(println)).run()

  /*
  Complex sink
   */
  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))
  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      broadcast ~> sink1
      broadcast ~> sink2
      SinkShape(broadcast.in)
    }
  )
//  firstSource.to(sinkGraph).run()

  /*
  Ex: complex flow
  - first: add 1 to a number
  - second: multiply the number by 10
   */
  val flow1 = Flow[Int].map(x => x + 1)
  val flow2 = Flow[Int].map(x => x * 10)
  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val shape1 = builder.add(flow1)
      val shape2 = builder.add(flow2)
      shape1 ~> shape2
      FlowShape(shape1.in, shape2.out)
    }
  )
  firstSource.via(flowGraph).to(Sink.foreach(println)).run()
}
