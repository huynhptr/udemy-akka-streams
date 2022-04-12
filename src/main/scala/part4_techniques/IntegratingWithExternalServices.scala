package part4_techniques

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import java.util.Date
import scala.concurrent.Future

object IntegratingWithExternalServices extends App {
  implicit val system = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer = ActorMaterializer()

  def genericExtService[A,B](element: A): Future[B] = ???

  // example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)
}
