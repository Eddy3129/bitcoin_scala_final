// src/main/scala/blockchain/Node.scala

package blockchain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import blockchain.Upnp.UpnpManager  // Updated import
import blockchain.Upnp.UpnpManager.AddPortMapping
import com.typesafe.config.ConfigFactory
import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._
import blockchain.actors.{Blockchainer, Peer}
import blockchain.controllers.MainController

object Node extends JFXApp {

  // Configuration parameters
  val hostname: String = "192.168.100.10" // Change to your IP configuration
  val port: Int = 2553 // Change for each new peer node, e.g., 2552, 2553...
  val seedNodes: List[String] = List(
    "akka://BitcoinNetwork@192.168.100.10:2551" // Change to your IP configuration
  )

  // Root behavior for the ActorSystem
  val rootBehavior: Behavior[Any] = Behaviors.ignore[Any]

  // Initialize the actor system
  val system: ActorSystem[Any] = ActorSystem(rootBehavior, "BitcoinNetwork", ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.hostname = "$hostname"
       |akka.remote.artery.canonical.port = $port
       |akka.cluster.seed-nodes = [${seedNodes.map(node => s""""$node"""").mkString(",")}]
       |""".stripMargin).withFallback(ConfigFactory.load())
  )

  // Spawn the Blockchainer actor
  val blockchainer: ActorRef[Blockchainer.Command] =
    system.systemActorOf(Blockchainer(), s"Blockchainer-$port")

  // Spawn the Peer actor with a reference to Blockchainer
  val peer: ActorRef[Peer.Command] =
    system.systemActorOf(Peer(blockchainer), s"Peer-$port")

  // Spawn the UpnpManager actor and add port mappings
  val upnpManager: ActorRef[UpnpManager.Command] =
    system.systemActorOf(UpnpManager(), s"UpnpManager-$port")
  upnpManager ! AddPortMapping(port)

  // Frontend Setup
  stage = new PrimaryStage {
    title = s"Bitcoin Node - $port"
    width = 1000
    height = 700

    val loader = new FXMLLoader(getClass.getResource("/blockchain/MainView.fxml"), NoDependencyResolver)
    loader.load()
    val root = loader.getRoot[javafx.scene.layout.AnchorPane]
    scene = new Scene(root)

    // Set up the controller with the actor system and actor references
    val controller = loader.getController[MainController#Controller]
    controller.setActorSystems(
      systems = List(system),
      blockchainerRefs = List(blockchainer),
      peerRefs = List(peer)
    )
  }

  // Ensure graceful shutdown
  sys.addShutdownHook {
    println("Shutting down...")
    system.terminate()
  }
}
