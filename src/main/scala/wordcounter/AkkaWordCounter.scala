package wordcounter

/**
  * Created by ssaif on 4/2/16.
  */
import java.io.File

import akka.actor._

import scala.concurrent.Future
import scala.io.Source

object StringCounterActor {
  def props = Props[StringCounterActor]

  case class ProcessStringMsg(lineNumber: Int, fileName: String, line: String, fileSender: Option[ActorRef], listener: ActorRef)
}

class StringCounterActor extends Actor {
  import Listener.CaptureStreamMsg
  import RoutingActor.StringProcessedMsg
  import StringCounterActor._

  def receive = {
    case ProcessStringMsg(lineNumber, fileName, line, rootSender, listener) => {
      var wordsInLine = 0
      if(line.length != 0)
      {
        wordsInLine = line.split(" ").length
      }

      try {
        listener ! CaptureStreamMsg(fileName, wordsInLine, lineNumber) //Streams word count to listener
        sender ! StringProcessedMsg(rootSender) //Sends a ping to the RoutingActor every time it finishes a task
      }
      catch {
        case e: Exception =>
          sender ! akka.actor.Status.Failure(e)
          throw e
      }
    }
    case _ => println("Error: message not recognized")
  }
}

object RoutingActor {
  def props(fileName: String, listener: ActorRef) = Props(new RoutingActor(fileName, listener))

  case class StringProcessedMsg(fileSender: Option[ActorRef])
  case object StartProcessFileMsg
}

class RoutingActor(fileName: String, listener: ActorRef) extends Actor {
  import Listener.CloseStreamMsg
  import RoutingActor._
  import StringCounterActor.ProcessStringMsg

  private var running = false
  private var totalLines = 0
  private var linesProcessed = 0
  private var startTime = 0L

  def receive = {
    case StartProcessFileMsg => {
      if (running) {
        println("Warning: duplicate start message received")
      } else {
        running = true
        startTime = System.nanoTime()
        val rootSender = Some(sender) // save reference to process invoker
        val lines = Source.fromFile(fileName)
        lines.getLines.foreach { line =>
          context.actorOf(Props[StringCounterActor]) ! ProcessStringMsg(totalLines, fileName, line, rootSender, listener)
          totalLines += 1
        }
      }
    }
    case StringProcessedMsg(rootSender) => {
      linesProcessed += 1

      if (linesProcessed == totalLines) {
        val stopTime = System.nanoTime()
        listener ! CloseStreamMsg(fileName, stopTime - startTime)
        rootSender match {
          case (Some(o)) => o ! linesProcessed // provide result to process invoker
        }
      }
    }
    case _ => println("message not recognized!")
  }
}

object Listener {
  def props = Props[Listener]

  case class CaptureStreamMsg(fileName: String, numOfWords: Int, lineNumber: Int)
  case class CloseStreamMsg(fileName: String, totalTime: Long)
}

class Listener extends Actor {
  import Listener._

  private var totalNumberOfWords = 0

  def receive = {
    case CaptureStreamMsg(fileName, numOfWords, lineNumber) =>
      totalNumberOfWords += numOfWords

    case CloseStreamMsg(fileName, totalTime) =>
      println("Stream Complete: " + fileName + " Total Number of Words: " + totalNumberOfWords +
        " Total Time: " + totalTime/1000000 + "ms")

    case _ => println("Error: message not recognized")
  }
}

object AkkaWordCounter extends App {

  import RoutingActor.StartProcessFileMsg
  import akka.pattern.ask
  import akka.util.Timeout

  import scala.concurrent.duration._

  override def main(args: Array[String]) {
    val sourceDirectoryName = if (args.length > 0) args(1) else "src/main/resources/"
    val directory = new File(sourceDirectoryName)
    if (directory.exists && directory.isDirectory) {
      val system = ActorSystem()
      implicit val ec = system.dispatcher
      val results = directory.listFiles.map(_.getAbsolutePath).map(processFile(system, _))
      Future.sequence(results.toIterable).onComplete { _ =>
        system.terminate()
      }
    }
  }

  def processFile(system: ActorSystem, fileName: String): Future[Any] = {
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(30 seconds)

    val listener = system.actorOf(Props[Listener], name = s"Listener:${fileName.replace('/', '_')}")
    val actor = system.actorOf(RoutingActor.props(fileName, listener))

    actor ? StartProcessFileMsg
  }
}

