package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._

object DynamicStreamHandling extends App {
  implicit val system = ActorSystem("DynamicStreamHandling")
  implicit val materializer = ActorMaterializer()

  // #1: Kill Switch
  val killSwitchFlow = KillSwitches.single[Int]

  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  val killSwitch = counter.viaMat(killSwitchFlow)(Keep.right).to(sink)
//    .run()

  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds) {
//    killSwitch.shutdown()
  }

  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

//  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
//  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)

  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown()
  }

  // MergeHub
  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  //use this sink any time we like
//  Source(1 to 10).runWith(materializedSink)
//  counter.runWith(materializedSink)9

  // BroadcastHub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 10).runWith(dynamicBroadcast)
//  materializedSource.runWith(Sink.ignore)
//  materializedSource.runWith(Sink.foreach[Int](println))

  /*
  Challenge - combine a mergeHub and a broadcastHub.
   */
  val (publisher, subscriber) = dynamicMerge.toMat(dynamicBroadcast)(Keep.both).run()
  Source(1 to 10).to(publisher).run()
  Source(11 to 20).to(publisher).run()
  subscriber.to(Sink.foreach[Int](x => println(s"Sink1: $x"))).run()
  subscriber.to(Sink.foreach[Int](x => println(s"Sink2: $x"))).run()
}
