package blockchain

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object SeedNodeStarter {

  // Define a sealed trait for the root command to ensure type safety
  sealed trait SeedNodeCommand

  def main(args: Array[String]): Unit = {
    // Define hostname and port for the seed node
    val hostname = "192.168.100.10" // Adjust based on your environment
    val port = 2551 // Adjust to the seed node's port

    // Load Akka configuration with seed node settings
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.hostname = "$hostname"
         |akka.remote.artery.canonical.port = $port
         |akka.cluster.seed-nodes = [
         |  "akka://BitcoinNetwork@$hostname:$port"
         |]
         |""".stripMargin
    ).withFallback(ConfigFactory.load())

    // Define an empty root behavior for the seed node
    val rootBehavior = Behaviors.empty[SeedNodeCommand]

    // Start the seed node ActorSystem
    val system = ActorSystem(rootBehavior, "BitcoinNetwork", config)

    println(s"Seed node started at $hostname:$port")
    sys.addShutdownHook {
      println("Shutting down seed node...")
      system.terminate()
    }
  }
}
