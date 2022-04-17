package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

object CustomOperators extends App {
  implicit val system = ActorSystem("CustomOperators")
  implicit val materializer = ActorMaterializer()

  //1 - a custom source which emits random numbers until cancelled
  class RandomNumberGenerator(max: Int) extends GraphStage[/*Step 0*/SourceShape[Int]] {

    // step 1: define the ports and the component-specific members
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2 - construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3 - create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // step 4 - define mutable state
        //implement my logic here
        setHandler(outPort, new OutHandler {
          // when there is demand from downstream
          override def onPull(): Unit = {
            // emit a new element
            val nextNumber = random.nextInt(max)
            // push it out of the outport
            push(outPort, nextNumber)
          }
        })
      }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
//  randomGeneratorSource.runWith(Sink.foreach(println))

  // 2 - a custom sink that prints elements in batches of a given size

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {
    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = new SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        override def preStart(): Unit = {
          pull(inPort)
        }

        //mutable state
        val batch = new mutable.Queue[Int]

        setHandler(inPort, new InHandler {
          //when the upstream wants to sent me an element
          override def onPush(): Unit = {
            val nextElement = grab(inPort)
            batch.enqueue(nextElement)

            //assume some complex computation ( for back pressure)
            Thread.sleep(100)

            if(batch.size >= batchSize) {
              println("New batch: " + batch.dequeueAll(_ => true).mkString("[",", ","]"))
            }

            pull(inPort) // send demand upstream
          }

          override def onUpstreamFinish(): Unit = {
            if (batch.nonEmpty) {
              println("New batch: " + batch.dequeueAll(_ => true).mkString("[",", ","]"))
              println("Stream finishes.")
            }
          }
        })
      }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))
//  randomGeneratorSource.to(batcherSink).run()

  /*
  excercise: a custom flow - a simple filter flow
  - 2 ports: an input port and an output port
   */
  class SimpleFilterFlow[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort = Inlet[T]("inputPort")
    val outPort = Outlet[T]("outputPort")

    override def shape: FlowShape[T, T] = FlowShape[T,T](inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        override def preStart(): Unit = pull(inPort)
        setHandlers(inPort,outPort , new InHandler with OutHandler {
          override def onPush(): Unit = {
            try {
              val nextElement = grab(inPort)
              if(predicate(nextElement)) {
                push(outPort, nextElement)
              }
              pull(inPort)
            } catch {
              case ex: Throwable => failStage(ex)
            }
          }
          override def onPull(): Unit = if (!hasBeenPulled(inPort)) pull(inPort)
        })
      }
  }
//  Source(1 to 10).via(new SimpleFilterFlow[Int](_ % 2 == 1)).to(Sink.foreach(println)).run()
  val myFilter = Flow.fromGraph(new SimpleFilterFlow[Int](_ > 50))
  randomGeneratorSource.via(myFilter).to(batcherSink).run()
  // backpressure works out of the box
  /*
  Materialized values in graph stages
   */

  // 3 - a flow that counts the number of elements that go through it
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T,T], Future[Int]] {
    val inPort: Inlet[T] = Inlet[T]("counterInt")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val graphStateLogic: GraphStageLogic = new GraphStageLogic(shape) {
        var counter = 0
        setHandlers(inPort, outPort, new InHandler with OutHandler {
          override def onPush(): Unit = {
            val nextElement = grab(inPort)
            counter += 1
            push(outPort, nextElement)
          }

          override def onPull(): Unit = pull(inPort)

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }
      (graphStateLogic, promise.future)
    }
  }
  val counterFlow = Flow.fromGraph(new CounterFlow[Int])
  val countFuture = Source(1 to 10)
//    .map(x => if (x > 7) throw new RuntimeException("gotcha") else x)
    .viaMat(counterFlow)(Keep.right)
    .to(Sink.foreach(x => if (x == 7) throw new RuntimeException else println(x)))
    .run()
  import system.dispatcher
  countFuture.onComplete({
    case Success(value) => println(s"The number of elements passed: $value")
    case Failure(exception) => println(s"Counting the elements failed: $exception")
  })
}
