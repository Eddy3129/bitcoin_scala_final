package blockchain

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import blockchain.actors.Blockchainer
import blockchain.model.TransactionItem

object Peer {
  val PeerServiceKey: ServiceKey[Command] = ServiceKey("PeerService")

  sealed trait Command
  case class BroadcastTransaction(transaction: TransactionItem) extends Command
  case object DiscoverPeers extends Command
  private case class PeerDiscovered(peers: Set[ActorRef[Command]]) extends Command

  def apply(blockchainer: ActorRef[Blockchainer.Command]): Behavior[Command] = {
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(PeerServiceKey, context.self)

      var peers: Set[ActorRef[Command]] = Set.empty

      Behaviors.receiveMessage {
        case DiscoverPeers =>
          context.system.receptionist ! Receptionist.Subscribe(PeerServiceKey, context.messageAdapter {
            case PeerServiceKey.Listing(listing) => PeerDiscovered(listing)
          })
          Behaviors.same

        case PeerDiscovered(newPeers) =>
          peers = newPeers
          Behaviors.same

        case BroadcastTransaction(transaction) =>
          if (blockchainer != null) {
            blockchainer ! Blockchainer.AddTransaction(transaction)
            peers.foreach(_ ! BroadcastTransaction(transaction))
          } else {
            context.log.error("Blockchainer actor is not initialized.")
          }
          Behaviors.same
      }
    }
  }
}
