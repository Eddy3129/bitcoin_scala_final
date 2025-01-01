// src/main/scala/blockchain/Upnp/UpnpManager.scala

package blockchain.Upnp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}
import org.fourthline.cling.support.model.PortMapping
import org.fourthline.cling.support.igd.PortMappingListener
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.model.message.header.STAllHeader
import org.fourthline.cling.registry.RegistryListener

import java.net.InetAddress

object UpnpManager {
  // Define the Command trait and AddPortMapping case class
  sealed trait Command
  case class AddPortMapping(port: Int) extends Command

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Starting Cling...")
      val upnpService: UpnpService = new UpnpServiceImpl(Cling.listener)
      Behaviors.receiveMessage[Command] {
        case AddPortMapping(port) =>
          val localAddress = InetAddress.getLocalHost().getHostAddress
          val mappings = List(
            new PortMapping(port, localAddress, PortMapping.Protocol.TCP, s"TCP Port Forwarding for port $port"),
            new PortMapping(port, localAddress, PortMapping.Protocol.UDP, s"UDP Port Forwarding for port $port")
          )
          val registryListener = new PortMappingListener(mappings.toArray)
          upnpService.getRegistry().addListener(registryListener)
          upnpService.getControlPoint().search(new STAllHeader())
          context.log.info(s"Added UPnP port mappings for port $port")
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }.receiveSignal {
        case (context, PostStop) =>
          upnpService.shutdown()
          context.log.info("Stopping Cling...")
          Behaviors.same
      }
    }
  }
}
