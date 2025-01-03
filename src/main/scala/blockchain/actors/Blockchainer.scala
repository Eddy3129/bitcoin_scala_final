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
  case class BlockMined(block: LinkedBlock) extends Command
  case class BlockchainState(blocks: List[Block], isMining: Boolean)
  case class TransactionReceived(transaction: TransactionItem) extends Command

  def apply(peer: ActorRef[Peer.Command]): Behavior[Command] = Behaviors.setup { context =>
    var blockchain: List[Block] = List(RootBlock)
    var transactionQueue: List[TransactionItem] = List.empty
    var isMiningPaused: Boolean = false
    var currentMiningBlock: Option[LinkedBlock] = None

    context.log.info(s"Initialized Blockchainer at ${context.self.path}")

    Behaviors.receiveMessage {
      case AddTransaction(transaction) =>
        if (!transactionQueue.exists(_.id == transaction.id)) {
          context.log.info(s"Received transaction: $transaction")
          transactionQueue :+= transaction
          context.system.eventStream.tell(akka.actor.typed.eventstream.EventStream.Publish(TransactionReceived(transaction)))
          // Add this line
        } else {
          context.log.info(s"Ignored duplicate transaction: $transaction")
        }
        Behaviors.same

      case MineBlock if transactionQueue.nonEmpty && !isMiningPaused =>
        val prevBlock = blockchain.head
        context.log.info(s"Starting mining with previous hash: ${prevBlock.hash}")

        isMiningPaused = true  // Set mining status
        val transactions = Transactions(transactionQueue, System.currentTimeMillis)
        transactionQueue = List.empty // Clear the queue after mining

        val newBlock = ProofOfWork.generateProof(transactions, prevBlock.hash, System.currentTimeMillis)
        currentMiningBlock = Some(newBlock)
        blockchain = newBlock :: blockchain

        context.log.info(s"Mined new block: ${newBlock.hash}, Nonce: ${newBlock.nonce}, Previous Hash: ${newBlock.hashPrev}")

        peer ! Peer.BroadcastBlock(newBlock)
        isMiningPaused = false  // Reset mining status
        Behaviors.same

      case MineBlock =>
        context.log.info("No transactions to mine or mining paused.")
        Behaviors.same

      case GetBlockchain(replyTo) =>
        replyTo ! BlockchainState(blockchain, isMiningPaused)
        Behaviors.same

      case BlockMined(block) =>
        if (currentMiningBlock.exists(_.hash == block.hash)) {
          // Skip validation if this is a self-mined block
          context.log.info(s"Ignored validation of self-mined block: ${block.hash}")
          currentMiningBlock = None
          isMiningPaused = false
          Behaviors.same
        } else {
          // Proceed with validation for blocks received from peers
          context.log.info(s"Validating block: ${block.hash} with nonce: ${block.nonce}")
          val lastBlock = blockchain.head
          val validBlock = ProofOfWork.calculateHash(block.transactions, block.hashPrev, block.timestamp, block.nonce) == block.hash

          if (validBlock && (block.hashPrev == lastBlock.hash || (lastBlock == RootBlock && block.hashPrev == RootBlock.hash))) {
            blockchain = block :: blockchain
            transactionQueue = transactionQueue.filterNot(t => block.transactions.items.exists(_.id == t.id)) // Remove mined transactions
            context.log.info(s"Validation successful. Added block to blockchain: ${block.hash}")
          } else {
            context.log.info(s"Validation failed. Block is invalid: ${block.hash}")
          }
          isMiningPaused = false
          Behaviors.same
        }
    }
    }
}
