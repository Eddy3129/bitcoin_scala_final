package blockchain.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import blockchain.ProofOfWork
import blockchain.model.{Block, LinkedBlock, RootBlock, TransactionItem, Transactions}

object Blockchainer {
  sealed trait Command
  case class AddTransaction(transaction: TransactionItem) extends Command
  case object MineBlock extends Command
  case class GetBlockchain(replyTo: ActorRef[BlockchainState]) extends Command
  case class BlockchainState(blocks: List[Block])
  case class BlockMined(block: LinkedBlock) extends Command

  def apply(peer: ActorRef[Peer.Command]): Behavior[Command] = Behaviors.setup { context =>
    var blockchain: List[Block] = List(RootBlock)
    var transactionQueue: List[TransactionItem] = List.empty
    var isMiningPaused: Boolean = false

    context.log.info(s"Initialized Blockchainer at ${context.self.path}")

    Behaviors.receiveMessage {
      case AddTransaction(transaction) =>
        if (!transactionQueue.exists(_.id == transaction.id)) {
          context.log.info(s"Received transaction: $transaction")
          transactionQueue :+= transaction
        } else {
          context.log.info(s"Ignored duplicate transaction: $transaction")
        }
        Behaviors.same

      case MineBlock if transactionQueue.nonEmpty && !isMiningPaused =>
        val prevBlock = blockchain.head
        context.log.info(s"Starting mining with previous hash: ${prevBlock.hash}")

        val transactions = Transactions(transactionQueue, System.currentTimeMillis)
        transactionQueue = List.empty // Clear the queue after mining

        val newBlock = ProofOfWork.generateProof(transactions, prevBlock.hash, System.currentTimeMillis)
        blockchain = newBlock :: blockchain

        context.log.info(s"Mined new block: ${newBlock.hash}, Nonce: ${newBlock.nonce}, Previous Hash: ${newBlock.hashPrev}")

        // Broadcast mined block to peers
        peer ! Peer.BroadcastBlock(newBlock)
        Behaviors.same

      case MineBlock =>
        context.log.info("No transactions to mine or mining paused.")
        Behaviors.same

      case GetBlockchain(replyTo) =>
        replyTo ! BlockchainState(blockchain)
        Behaviors.same

      case BlockMined(block) =>
        isMiningPaused = true // Pause mining during block validation
        val lastBlock = blockchain.head
        context.log.info(s"Received block with hash: ${block.hash}, Previous Hash: ${block.hashPrev}, Last Block Hash: ${lastBlock.hash}")

        if ((lastBlock == RootBlock && block.hashPrev == RootBlock.hash) || block.hashPrev == lastBlock.hash) {
          blockchain = block :: blockchain
          transactionQueue = transactionQueue.filterNot(t => block.transactions.items.exists(_.id == t.id)) // Remove mined transactions
          context.log.info(s"Added received block to blockchain: ${block.hash}")
        } else {
          context.log.info(s"Rejected block with invalid hashPrev: ${block.hashPrev}")
        }
        isMiningPaused = false // Resume mining
        Behaviors.same
    }
  }
}
