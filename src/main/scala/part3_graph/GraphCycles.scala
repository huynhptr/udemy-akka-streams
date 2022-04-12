package part3_graph

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy, SourceShape, UniformFanInShape}

object GraphCycles extends App {
  implicit val system = ActorSystem("GraphCycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })
    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape
    ClosedShape
  }
  //  RunnableGraph.fromGraph(accelerator).run() // graph cycle deadlock!

  /*
  Solution 1: MergePreferred
   */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })
    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape
    ClosedShape
  }
  //  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
  Solution 2: buffers
   */
  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val repeaterShape =
      builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
        println(s"Accelerating $x")
        Thread.sleep(100)
        x
      })
    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape
    ClosedShape
  }
  //  RunnableGraph.fromGraph(bufferedRepeater).run()

  /*
  cycles risk deadlocking
  - add bounds to the number of elements in the cycle

  boundedness vsl liveness
   */
  /*
  Challenge: create a fan-in shape
  - two inputs which will be fed exactly one number (1 and 1)
  - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
  1, 2, 3, 5, 8, ...
  Hint: Use ZipWith and cycles, MergePreferred
   */
  //my solution
  val oneSource = Source.single(1).map { x => println(x); x }
  val mergePreferred = MergePreferred[Int](1)
  val broadcast = Broadcast[Int](2)
  val zipWith = ZipWith[Int, Int, Int]((a, b) => a + b)
  val printFlow = Flow[Int].map { i =>
    println(i)
    Thread.sleep(1000)
    i
  }
  val fibonacciGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val source1 = builder.add(oneSource)
    val source2 = builder.add(oneSource)
    val merge1 = builder.add(mergePreferred)
    val merge2 = builder.add(mergePreferred)
    val bc = builder.add(broadcast)
    val zw = builder.add(zipWith)
    val flow = builder.add(printFlow)
    source1 ~> merge1 ~> zw.in0
    source2 ~> merge2 ~> bc ~> zw.in1
    merge2.preferred <~ flow <~ zw.out
    merge1.preferred <~ bc
    ClosedShape
  }
  //  RunnableGraph.fromGraph(fibonacciGraph).run()

  //easier solution
  val easyFiboGraph = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val source1 = builder.add(Source.single(1))
      val source2 = builder.add(Source.single(1))
      val zipGraph = builder.add(Zip[BigInt, BigInt])
      val mpGraph = builder.add(MergePreferred[(BigInt, BigInt)](1))
      val calFlow = builder.add(Flow[(BigInt, BigInt)].map {tup =>
        Thread.sleep(1000)
        (tup._1 + tup._2, tup._1) // tup = (last, previous)
      })
      val bc = builder.add(Broadcast[(BigInt, BigInt)](2))
      val fiboSink = builder.add(Sink.foreach[(BigInt, BigInt)]{tup => println(tup._1)})
      source1 ~> zipGraph.in0
      source2 ~> zipGraph.in1
      zipGraph.out ~> mpGraph ~> calFlow ~> bc ~> fiboSink
      mpGraph.preferred <~ bc.out(1)
      ClosedShape
    }
  }
  easyFiboGraph.run()
}