package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object Backpressure extends App {
  implicit val system = ActorSystem("Backpressure")
  implicit val meterializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

//  fastSource.to(slowSink).run() //no backpressure because this run on same actor
//  fastSource.async.to(slowSink).run() // there is backpressure because this run on 2 actors

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

//  fastSource.async.via(simpleFlow).async.to(slowSink).run()

  /*
  reactions to backpressure (in order):
  - try to slow down if possible
  - buffer elements until there's more demand
  - drop down elements from the buffer if it overflows
  - tear down/kill the whole stream(failure)
   */
  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
//  fastSource.async.via(bufferedFlow).async.to(slowSink).run()

  //throttling
  import scala.concurrent.duration._
  fastSource.throttle(2, 1 second).runWith(Sink.foreach(println))


//  system.terminate()
}
