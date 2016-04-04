package wordcounter

/**
  * Created by ssaif on 4/2/16.
  */
import java.io.InputStream
import java.io.File

import akka.actor._

//Create all the message types
case class ProcessStringMsg(lineNumber: Int, fileName: String, line: String, listener: ActorRef)
case class StringProcessedMsg()
case class CaptureStreamMsg(fileName: String, numOfWords: Int, lineNumber: Int)
case class CloseStreamMsg(totalTime: Long, fileName: String)
case class StartProcessFileMsg()
case class IncrementFileCounter()

//A class to hold the file name and input stream
class FileReference(val fileName: String, val stream: InputStream)

class StringCounterActor extends Actor {
  def receive = {
    case ProcessStringMsg(lineNumber, fileName, line, listener) => {
      var wordsInLine = 0
      if(line.length != 0)
      {
        wordsInLine = line.split(" ").length
      }

      try {
        listener ! CaptureStreamMsg(fileName, wordsInLine, lineNumber) //Streams word count to listener
        sender ! StringProcessedMsg() //Sends a ping to the RoutingActor every time it finishes a task
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



class RoutingActor(fileRef: FileReference, listener: ActorRef, incrementor: ActorRef) extends Actor {

  private var running = false
  private var totalLines = 0
  private var linesProcessed = 0
  private val fileName = fileRef.fileName
  private var startTime = 0L

  def receive = {
    case StartProcessFileMsg() => {
      if (running) {
        println("Warning: duplicate start message received")
      } else {
        running = true
        startTime = System.nanoTime()
        val lines = scala.io.Source.fromInputStream(fileRef.stream)
        lines.getLines.foreach { line =>
          context.actorOf(Props[StringCounterActor]) ! ProcessStringMsg(totalLines, fileName, line, listener)
          totalLines += 1
        }
        lines.close()
      }
    }
    case StringProcessedMsg() => {
      linesProcessed += 1

      if (linesProcessed == totalLines) {
        //A file has completed processing
        val stopTime = System.nanoTime()
        listener ! CloseStreamMsg(stopTime-startTime, fileName)
        incrementor ! IncrementFileCounter()
      }
    }
    case _ => println("message not recognized!")
  }
}

class Listener() extends Actor {
  private var totalNumberOfWords = 0

  def receive = {

    case CaptureStreamMsg(fileName, numOfWords, lineNumber) =>
      totalNumberOfWords += numOfWords
    //println(fileName + " " + "L." + lineNumber + " " + numOfWords + " words")
    //Stream results to Client

    case CloseStreamMsg(totalTime, fileName) =>
      println("Stream Complete: " + fileName + " Total Number of Words: " + totalNumberOfWords +
        " Total Time: " + totalTime/1000000 + "ms")

    case _ => println("Error: message not recognized")
  }
}

class Incrementor(totalNumOfFiles: Int, system: ActorSystem) extends Actor {

  private var fileCounter = 0

  def receive = {
    case IncrementFileCounter() =>
          fileCounter += 1
          if(totalNumOfFiles == fileCounter)
            {
              system.terminate()
            }

    case _ => println("Error: message not recognized")
  }
}

object AkkaWordCounter extends App {

  import akka.util.Timeout
  import scala.concurrent.duration._
  import akka.pattern.ask
  import akka.dispatch.ExecutionContexts._

  private var numberOfFiles = 0

  override def main(args: Array[String]) {


    val system = ActorSystem("ActorSystem")

    val files = getListOfFiles("src/main/resources/")

    /**
      * foreach takes a procedure -- a function with a result type Unit -- as the right operand.
      * It simply applies the procedure to each List element.
      * The result of the operation is again Unit; no list of results is assembled.
      */
    numberOfFiles = files.length
    //The incrementor keeps track of files processed and terminates the system.
    val incrementor = system.actorOf(Props(new Incrementor(numberOfFiles, system)))
    files.foreach(fileName => initActorSystem(fileName, system, incrementor))

  }

  def initActorSystem(fileName: String, system: ActorSystem, incrementor: ActorRef): Unit = {
    //Fixing bug from original code: https://www.toptal.com/scala/concurrency-and-fault-tolerance-made-easy-an-intro-to-akka#comment-1776147740
    implicit val executionContext = global
    // create the result listener, which will print the result. Didn't give a name, so it will be unique every time
    val listener = system.actorOf(Props[Listener])
    //Load from /resources folder: http://stackoverflow.com/questions/27360977/how-to-read-files-from-resources-folder-in-scala
    val stream : InputStream = getClass.getResourceAsStream("/" + fileName)
    //Again Didn't give the actor a name, so it will be unique every time
    val routingActor = system.actorOf(Props(new RoutingActor(new FileReference(fileName, stream), listener, incrementor)))
    implicit val timeout = Timeout(5 seconds)

    /**
      * Messages are sent to an Actor through one of the following methods.
      * ! means “fire-and-forget”, e.g. send a message asynchronously and return immediately. Also known as tell.
      * ? sends a message asynchronously and returns a Future representing a possible reply. Also known as ask.
      */

    /**
      * Option One: Do an Ask call
      * http://doc.akka.io/docs/akka/current/scala/futures.html
      * When the future returns after all the work is complete for each file
      */

//    val futureResult = routingActor ? StartProcessFileMsg()
//    futureResult.map { result =>
//      //"result" can be also be an object. You simply have to do result.asInstanceOf[ObjectName]
//      //println("Number of lines processed in " + fileName + ": " + result)
//    }

    /**
      * Option two: Do a Tell call
      * http://doc.akka.io/docs/akka/2.4.1/scala/actors.html#Send_messages
      * Except from "Effective Akka"
      * Tell, don’t ask
      * A best practice for actor development is to avoid ask: use fire and forget messages, and be prepared to handle responses.
      * This is simpler and more expressive than using futures and having to map over composed responses,
      * but I’m also only using one asynchronous threaded resource (the anonymous actor I created) as opposed to multiple futures for
      * the sends and the myriad futures returned within a composed for comprehension to handle the results. Always try to focus on tell over ask.
      * Truly fault-resilient systems send messages in FIRE AND FORGET fashion and prepare to receive the expected response.
      * They do not make assumptions that sending once means that the message was definitely received and is being handled, since anything can happen in between.
      * As such, we should schedule a task to continually resend that message a pre-defined number of times in some acceptable duration.
      * If no expected response is received within that timeframe, we should be able to handle or escalate the failure.
      *
      */
    //FIRE AND FORGET
     routingActor ! StartProcessFileMsg()

  }

  def getListOfFiles(dir: String):List[String] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.map(file => file.getName).toList
    } else {
      List[String]()
    }
  }
}

