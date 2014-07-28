package smtpServer

import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import akka.routing.SmallestMailboxRouter
import akka.actor.ActorDSL._
import java.net.InetSocketAddress
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.io.BufferedReader;

 
/**
 * The email message sent to Actors in charge of delivering email
 *
 * @param subject the email subject
 * @param simple text
 */
case class EmailMessage(
						 email: String,
                         subject: String,
                         text: String
                         )
                         

object SmtpServer extends App {
  
  class Server extends Actor with ActorLogging{
    import Tcp._
    import context.system
    
    IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 8080))
 
    def receive = {
    	case b @ Bound(localAddress) => log.info("Bound")
    	// do some logging or setup ...
 
    	case CommandFailed(_: Bind) => context stop self//context stop self
 
    	case c @ Connected(remote, local) => log.info("Connected")
    		val handler = context.actorOf(Props(new SimplisticHandler(sender, remote)))
    		val connection = sender()
    		connection ! Register(handler)
    }
    
  }
  
  class SimplisticHandler(connection: ActorRef, remote: InetSocketAddress) extends Actor with ActorLogging{
	  import Tcp._
	  
	  context watch connection
	  
	  def receive = {
	  	case Received(data) => sender() ! Write(ByteString((new String("From Server: Message Received")).toCharArray.map(_.toByte)))
	  	  		val message = data.utf8String.split("\n", 3)
	  			val mailbox = context.actorOf(Props(new mailboxActor(connection)))
	  			mailbox ! new EmailMessage(message(0), message(1), message(2))
	  			
	  	case PeerClosed     => context stop self
	  }
  }
  
  class mailboxActor(connection: ActorRef) extends Actor with ActorLogging{
    import Tcp._
	  def receive = {
      	case EmailMessage(email, subject, text) => log.info(s"To: $email\n Subject: $subject\nBody: $text" )
      			context stop self
	  }
  }
  
  ActorSystem().actorOf(Props(new Server))
  
}