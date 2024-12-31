package blockchain

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import blockchain.actors.Blockchainer
import blockchain.controllers.MainController
import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._

object Main extends JFXApp {

  // Define root behavior to manage Peer and Blockchainer actors
  val rootBehavior: Behavior[Peer.Command] = Behaviors.setup { context =>
    val blockchainer = context.spawn(Blockchainer(), "Blockchainer")
    val peer = context.spawn(Peer(blockchainer), "Peer")

    // Handle Peer.Command messages at the root
    Behaviors.receiveMessage {
      case msg: Peer.Command =>
        peer ! msg
        Behaviors.same
    }
  }

  // Initialize ActorSystem
  val actorSystem: ActorSystem[Peer.Command] = ActorSystem(rootBehavior, "BitcoinNetwork")

  // Load FXML
  val loader = new FXMLLoader(getClass.getResource("/MainView.fxml"), NoDependencyResolver)
  try {
    loader.load()
    val controller = loader.getController[MainController#Controller]
    controller.setActorSystem(actorSystem)
  } catch {
    case ex: Exception =>
      println(s"Error loading FXML: ${ex.getMessage}")
      sys.exit(1)
  }

  // Setup UI
  stage = new JFXApp.PrimaryStage {
    title = "Peer-to-Peer Blockchain"
    scene = new Scene(loader.getRoot[javafx.scene.layout.AnchorPane])
    onCloseRequest = _ => {
      println("Shutting down ActorSystem...")
      actorSystem.terminate()
    }
  }
}
