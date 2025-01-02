package blockchain.actors

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import blockchain.model.{LinkedBlock, TransactionItem, JsonSerializable}

object Peer {
  val PeerServiceKey: ServiceKey[Command] = ServiceKey("PeerService")

  sealed trait Command extends JsonSerializable
  case class BroadcastTransaction(transaction: TransactionItem) extends Command
  case class BroadcastBlock(block: LinkedBlock) extends Command
  case class UpdateBlockchainer(blockchainer: ActorRef[Blockchainer.Command]) extends Command
  case object DiscoverPeers extends Command
  private case class PeerDiscovered(peers: Set[ActorRef[Command]]) extends Command

  def apply(initialBlockchainer: ActorRef[Blockchainer.Command]): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info(s"Initializing Peer ${context.self.path.name}")

      // Register with the Receptionist
      context.system.receptionist ! Receptionist.Register(PeerServiceKey, context.self)
      context.log.info(s"Peer ${context.self.path.name} registered with Receptionist")

      // Create a listing adapter
      val listingAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter { listing =>
          PeerDiscovered(listing.serviceInstances(PeerServiceKey))
        }
      context.system.receptionist ! Receptionist.Subscribe(PeerServiceKey, listingAdapter)

      var peers: Set[ActorRef[Command]] = Set.empty
      var receivedBlocks: Set[String] = Set.empty
      var transactionQueue: List[TransactionItem] = List.empty
      var processedTransactions: Set[String] = Set.empty // Tracks processed transaction IDs
      var blockchainer: ActorRef[Blockchainer.Command] = initialBlockchainer // Dynamic blockchainer reference

      Behaviors.receiveMessage {
        case DiscoverPeers =>
          context.log.info(s"Discovering peers for ${context.self.path.name}")
          context.system.receptionist ! Receptionist.Find(PeerServiceKey, listingAdapter)
          Behaviors.same

        case PeerDiscovered(newPeers) =>
          peers = newPeers - context.self
          context.log.info(s"Discovered peers: ${peers.size}, Peers: ${peers.map(_.path.name)}")
          Behaviors.same

        case BroadcastTransaction(transaction) =>
          if (!processedTransactions.contains(transaction.id)) {
            context.log.info(s"Peer at ${context.self.path} received new transaction: $transaction")
            processedTransactions += transaction.id
            blockchainer ! Blockchainer.AddTransaction(transaction)

            peers.foreach { peer =>
              if (peer != context.self) {
                context.log.info(s"Broadcasting transaction to peer: ${peer.path}")
                peer ! BroadcastTransaction(transaction)
              }
            }
          } else {
            context.log.info(s"Peer at ${context.self.path} ignored duplicate transaction: $transaction")
          }
          Behaviors.same

        case BroadcastBlock(block) =>
          if (!receivedBlocks.contains(block.hash)) {
            receivedBlocks += block.hash
            context.log.info(s"Broadcasting block to peers: ${block.hash}")
            peers.foreach { peer =>
              if (peer != context.self) {
                peer ! BroadcastBlock(block)
              }
            }
            blockchainer ! Blockchainer.BlockMined(block)
          } else {
            context.log.info(s"Ignored duplicate block: ${block.hash}")
          }
          Behaviors.same

        case UpdateBlockchainer(newBlockchainer) =>
          blockchainer = newBlockchainer
          context.log.info(s"Updated Blockchainer reference for Peer: ${context.self.path}")
          Behaviors.same
      }
    }
  }
}
