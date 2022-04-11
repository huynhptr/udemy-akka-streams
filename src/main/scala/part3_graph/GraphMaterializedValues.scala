package part3_graph

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.parsing.combinator.Parsers
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  implicit val system = ActorSystem("GraphMaterializedValues")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("Akka","is","awesome","rock","the","jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int,String](0)((count, _) => count + 1)

  /*
  A composite component (sink)
  - prints out all strings which are lowercase
  - counts all strings that are short (< 5 chars)
   */
  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(counter) { implicit builder => counterShape =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFlow = builder.add(Flow[String].filter(s => s.compareTo(s.toLowerCase) == 0))
      val shortStringFlow = builder.add(Flow[String].filter(s => s.length < 5))

      broadcast ~> lowercaseFlow ~> printer
      broadcast ~> shortStringFlow ~> counterShape

      SinkShape(broadcast.in)
    }
  )
  val complexWordSink2 = Sink.fromGraph(
    GraphDSL.create(printer, counter) ((printerMat, counterMat) => counterMat) {
      implicit builder => (printerShape,counterShape) =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFlow = builder.add(Flow[String].filter(s => s.compareTo(s.toLowerCase) == 0))
      val shortStringFlow = builder.add(Flow[String].filter(s => s.length < 5))

      broadcast ~> lowercaseFlow ~> printerShape
      broadcast ~> shortStringFlow ~> counterShape

      SinkShape(broadcast.in)
    }
  )
  val shortWordsCount = wordSource.runWith(complexWordSink2)
  import system.dispatcher
  shortWordsCount.onComplete {
    case Success(count) => println(s"Short words count: $count")
    case Failure(ex) => println(s"Short words count failed with $ex")
  }
  /*
  Ex:
   */
  def enhanceFlow[A,B](flow: Flow[A,B,_]): Flow[A,B,Future[Int]] = {
    val counterSink = Sink.fold[Int,B](0)((count, _) => count +1)
    Flow.fromGraph {
      GraphDSL.create(counterSink) { implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[B](2))
        val flowGraph = builder.add(flow)
        flowGraph ~> broadcast
                     broadcast.out(1) ~> counterSinkShape
        FlowShape(flowGraph.in, broadcast.out(0))
      }
    }
  }
  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x)
  val simpleSink = Sink.ignore
  val enhancedFlowCountFuture = simpleSource
    .viaMat(enhanceFlow(simpleFlow))(Keep.right)
    .toMat(simpleSink)(Keep.left).run()
  enhancedFlowCountFuture.onComplete {
    case Success(count) => println(s"Elements through flow: $count")
    case Failure(ex) => println(s"Stream failed with $ex")
  }
}
