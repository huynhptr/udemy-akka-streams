package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {
  implicit val system = ActorSystem("firstPrinciples")
  implicit val materializer = ActorMaterializer()

  val source = Source(1 to 10)
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
//  graph.run()

  val flow = Flow[Int].map(i => i + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

//  sourceWithFlow.to(sink).run()
//  source.to(flowWithSink).run()
//  source.via(flow).to(sink).run()

  //illegal source
//  val illegalSource = Source.single[String](null)
//  illegalSource.to(Sink.foreach(println)).run()

  //various kinds of source
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1,2,3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1))
  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.fromFuture(Future(42))

  //various sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int,Int](0)(_ + _)

  //various flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5)
  //drop, filter, but NO flatmap

  // source -> flow -> flow -> ... -> flow -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
//  doubleFlowGraph.run()

  //syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2) // equivalent to Source(1 to 10).via(Flow[Int].map(x => x * 2))
  // run streams directly
//  mapSource.runForeach(println) // equivalent to mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = components

  /*
  Exercise: create a stream that takes the names of persons,
  then you will keep the first 2 names with length > 5 characters
   */

  val namesSource = Source(List("Andrew","Rick","Joe","Jessica","Kim","Martin"))
  val length5Flow = Flow[String].filter(s => s.length > 5)
  val take2Flow = Flow[String].take(2)
  val nameGraph = namesSource.via(length5Flow).via(take2Flow).to(Sink.foreach(println))
//  nameGraph.run()
  namesSource.filter(_.length > 5).take(2).runForeach(println) //one-liner


  system.terminate()
}
