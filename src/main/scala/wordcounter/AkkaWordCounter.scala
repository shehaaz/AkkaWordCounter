package wordcounter

/**
  * Created by ssaif on 4/2/16.
  */
import java.io.InputStream
import java.io.File

import akka.actor._

//Create all the message types
case class ProcessStringMsg(lineNumber: Int, fileName: String, line: String, fileSender: Option[ActorRef], listener: ActorRef)
case class StringProcessedMsg(fileSender: Option[ActorRef])
case class CaptureStreamMsg(fileName: String, numOfWords: Int, lineNumber: Int)
case class closeStreamMsg(totalTime: Long, fileName: String)
case class StartProcessFileMsg()

//A class to hold the file name and input stream
class FileReference(val fileName: String, val stream: InputStream)

class StringCounterActor extends Actor {
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



class RoutingActor(fileRef: FileReference, listener: ActorRef) extends Actor {

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
        val rootSender = Some(sender) // save reference to process invoker
        val lines = scala.io.Source.fromInputStream(fileRef.stream)
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
        listener ! closeStreamMsg(stopTime-startTime, fileName)
        rootSender match {
          case (Some(o)) => o ! linesProcessed // provide result to process invoker (i.e: futureResult)
        }
      }
    }
    case _ => println("message not recognized!")
  }
}

class Listener extends Actor {
  private var totalNumberOfWords = 0

  def receive = {

    case CaptureStreamMsg(fileName, numOfWords, lineNumber) =>
      totalNumberOfWords += numOfWords
    //println(fileName + " " + "L." + lineNumber + " " + numOfWords + " words")
    //Stream results to Client

    case closeStreamMsg(totalTime, fileName) =>
      println("Stream Complete: " + fileName + " Total Number of Words: " + totalNumberOfWords +
        " Total Time: " + totalTime/1000000 + "ms")

    case _ => println("Error: message not recognized")
  }
}

object AkkaWordCounter extends App {

  import akka.util.Timeout
  import scala.concurrent.duration._
  import akka.pattern.ask
  import akka.dispatch.ExecutionContexts._

  private var fileCounter = 0
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
    files.foreach(x => initActorSystem(x, system))

  }

  def initActorSystem(fileName: String, system: ActorSystem): Unit = {
    //Fixing bug from original code: https://www.toptal.com/scala/concurrency-and-fault-tolerance-made-easy-an-intro-to-akka#comment-1776147740
    implicit val executionContext = global
    // create the result listener, which will print the result. Didn't give a name, so it will be unique every time
    val listener = system.actorOf(Props[Listener])
    //Load from /resources folder: http://stackoverflow.com/questions/27360977/how-to-read-files-from-resources-folder-in-scala
    val stream : InputStream = getClass.getResourceAsStream("/" + fileName)
    //Again Didn't give the actor a name, so it will be unique every time
    val routingActor = system.actorOf(Props(new RoutingActor(new FileReference(fileName, stream), listener)))
    implicit val timeout = Timeout(5 seconds)
    //When the future returns after all the work is complete for each file
    val futureResult = routingActor ? StartProcessFileMsg()
    futureResult.map { result =>
      //println("Number of lines processed in " + fileName + ": " + result)
      //Increment the file counter when a result is received
      fileCounter += 1
      if(fileCounter == numberOfFiles)
        {
          //Terminate Actor System when all the files have been processed
          system.terminate()
        }
    }

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

