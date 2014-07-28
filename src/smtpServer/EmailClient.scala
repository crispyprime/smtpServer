import scala.swing._
import scala.swing.BorderPanel.Position._
import event._
import akka.actor._
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import scala.util.matching._

case class EmailMessage(
                         subject: String,
                         text: String
                         )

object EmailClient extends SimpleSwingApplication {
  
  val matches = "[a-zA-Z0-9]+@[a-zA-Z0-9]+[.][a-zA-Z]+\\z".r
  
    def top = new MainFrame {
      title = "Email Client"
        
      val connect = new Button {
        text = "Connect"
      }
      val send = new Button {
        text = "Send"
      }
      
      val toLabel = new Label {
        text = "To:      "
      }
      val subLabel = new Label {
        text = "Subject: "
      }
      val messageLabel = new Label {
        text = "Message: "
      }
  

      val email = new TextField{
    	columns = 20
      }
      val subject = new TextField{
    	columns = 20
      }
      val message = new TextArea {
    	preferredSize = new Dimension(200, 200)
      }

      contents = new FlowPanel {
        contents += connect
        contents += new Label {preferredSize = new Dimension(170, 10)}
        contents += toLabel
        contents += email
        contents += subLabel
        contents += subject
        contents += messageLabel
        contents += message
        contents += send
      }
      
   
      
      size = new Dimension(300, 400)
      menuBar = new MenuBar {
        contents += new Menu("File") {
          contents += new MenuItem(Action("Exit") {
            sys.exit(0)
          })
        }
      }
      
      val worker = createActorSystemWithActors()
      
      listenTo(connect)
      listenTo(send)
      
      reactions += {
        case ButtonClicked(component) if component == connect =>
          worker ! "connect"
        case ButtonClicked(component) if component == send =>
          if((matches findAllIn email.text).hasNext)
        	  worker ! ByteString((new String(email.text + "\n" + subject.text + "\n" + message.text)).toCharArray.map(_.toByte))
          else
            println("Invaild Email")
      }
      
    }
    
    def createActorSystemWithActors(): ActorRef = {
       val system = ActorSystem("Client")
       val endpoint = new InetSocketAddress("localhost", 8080)
       
       system.actorOf(ClientService.props(endpoint))
    }
    
}

object ClientService {
    def props(endpoint: InetSocketAddress): Props =
    Props(new ClientService(endpoint))
}

class ClientService(endpoint: InetSocketAddress) extends Actor with ActorLogging {
  import Tcp._
  import context.system
  
  
  override def receive: Receive = {
    case CommandFailed(_: Connect) => 
      log.info("Failed")
      context stop self
      
    case c @ Connected(remote, local) => 
      log.info("Connected")
      val connection = sender()
      connection ! Register(self)
      context become {
        case data: ByteString =>
          connection ! Write(data)
        case Received(data) =>
          log.info(data.utf8String)
      }
    case "connect" => IO(Tcp) ! Connect(endpoint)
    
  }	
}



