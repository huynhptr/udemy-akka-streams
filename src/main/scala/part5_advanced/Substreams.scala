package part5_advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {
  implicit val system = ActorSystem("Substreams")
  implicit val materialized = ActorMaterializer()
  import system.dispatcher

  // 1 - grouping a stream by a certain function
  val wordsSource = Source(List("Akka","is","amazing","learning","substreams"))
  val groups = wordsSource.groupBy(30,
    string => if(string.isEmpty) '\0' else string.toLowerCase.charAt(0))
  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"I just received $word, count is $newCount")
    newCount
  }))
//    .run()

  // 2 - merge substreams back
  val textSource = Source(List(
    "I love Akka Streams",
    "This is amazing",
    "learning from Rock the JVM"
  ))

  val totalCharacterCountFuture = textSource.groupBy(2, sentence => sentence.length % 2)
    .map(_.length) // do your expensive computation here
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_+_))(Keep.right)
//    .run()

//  totalCharacterCountFuture.onComplete {
//    case Success(value) => println(s"Total char count: $value")
//    case Failure(exception) => println(s"Char computation failed: $exception")
//  }

  // 3 - splitting a stream into substreams, when a condition is met
  val longString = "I love Akka Streams\n" + "this is amazing\n" + "learning from Rock the JVM\n"
  val charSource = Source(longString.toList)

  val charCountFuture = charSource.splitWhen(_ == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_+_))(Keep.right)
    .run()

  charCountFuture.onComplete {
    case Success(value) => println(s"Total char count: $value")
    case Failure(exception) => println(s"Char computation failed: $exception")
  }

  // 4 - flattening
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to x*3)).runWith(Sink.foreach[Int](println))
  simpleSource.flatMapMerge(2, x => Source(x to x*3)).runWith(Sink.foreach[Int](println))
}
