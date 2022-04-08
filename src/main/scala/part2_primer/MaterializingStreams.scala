package part2_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
//  val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int](_ + _)
//  val sumFuture = source.runWith(sink)

  import system.dispatcher
//  sumFuture.onComplete {
//    case Success(value) => println(s"The sum of all elements is: $value")
//    case Failure(ex) => println(s"The sum of the elements could not be computed: $ex")
//  }

  //choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)
  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
//  graph.run().onComplete {
//    case Success(_) => println("Stream processing finished.")
//    case Failure(ex) => println(s"Stream failed with $ex")
//  }

  // sugars
  val sum = Source(1 to 10).runWith(Sink.reduce[Int](_+_))
//  Source(1 to 10).runReduce(_+_)
  //backwards
//  Sink.foreach(println).runWith(Source.single(42))
  //both ways
//  Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)

  /*
  return the last element out of a source (use Sink.last)
  and total word count out of a stream of sentences (use map, fold, reduce)
   */
  val last = Source(1 to 10).toMat(Sink.last)(Keep.right).run().onComplete {
    case Success(x) => println(s"last: $x")
    case Failure(ex) => println(s"last failed with $ex")
  }
  val last2 = Source(1 to 10).runWith(Sink.last).onComplete {
    case Success(x) => println(s"last2: $x")
    case Failure(ex) => println(s"last2 failed with $ex")
  }

  val sentences = List("I like Akka","I wake up","run to the store","then start coding")
  val count1 = Source(sentences).runFold(0)((acc, s) => acc + s.split(" ").length).onComplete {
    case Success(x) => println(s"count1: $x")
    case Failure(ex) => println(s"count1 failed with $ex")
  }
  val count2 = Source(sentences).map(s => s.split(" ").length).runReduce(_+_).onComplete {
    case Success(x) => println(s"count2: $x")
    case Failure(ex) => println(s"count2 failed with $ex")
  }
  val count3 = Source(sentences).fold(0)((acc, s) => acc + s.split(" ").length)
    .runWith(Sink.last).onComplete {
    case Success(x) => println(s"count3: $x")
    case Failure(ex) => println(s"count3 failed with $ex")
  }
//  system.terminate()
}
