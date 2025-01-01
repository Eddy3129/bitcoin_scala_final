package blockchain

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import blockchain.actors.Blockchainer
import blockchain.controllers.MainController
import javafx.{scene => jfxs}
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
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

  // Configure the primary stage
  stage = new PrimaryStage {
    // Retrieve the port from the configuration
    val port = actorSystem.settings.config.getInt("akka.remote.artery.canonical.port")
    title = s"Bitcoin Scala - Node ${port - 2550}"
    width = 1000
    height = 700
  }

  // Load the main application UI
  def showMainApp(): Unit = {
    // Use absolute path by starting with '/'
    val resource = getClass.getResource("/blockchain/MainView.fxml")
    if (resource == null) {
      throw new IllegalArgumentException("Cannot find MainView.fxml")
    }
    val loader = new FXMLLoader(resource, NoDependencyResolver)
    loader.load()

    // Set the scene with the loaded root node
    val root = loader.getRoot[jfxs.layout.AnchorPane]
    stage.scene = new Scene(root)

    // Set the actor system for the controller
    val controller = loader.getController[MainController#Controller]
    controller.setActorSystem(actorSystem)
  }

  // Show the main application window
  showMainApp()

  // Close the window and terminate ActorSystem when "X" button is pressed
  stage.setOnCloseRequest { _ =>
    println("System is closing...")
    actorSystem.terminate()
    sys.exit()
  }
}
