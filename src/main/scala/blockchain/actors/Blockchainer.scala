package blockchain.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.eventstream.EventStream
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

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    var blockchain: List[Block] = List(RootBlock)
    var transactionQueue: List[TransactionItem] = List.empty

    context.log.info(s"Initialized Blockchainer at ${context.self.path}")

    Behaviors.receiveMessage {
      case AddTransaction(transaction) =>
        context.log.info(s"Blockchainer at ${context.self.path} received transaction: $transaction")
        transactionQueue :+= transaction
        context.log.info(s"Transaction queue updated: $transactionQueue")
        Behaviors.same

      case MineBlock if transactionQueue.nonEmpty =>
        context.log.info("Mining block with transactions...")
        context.log.info(s"Transactions to mine: ${transactionQueue.map(t => s"${t.sender} -> ${t.recipient}: ${t.amount}")}")

        val transactions = Transactions(transactionQueue, System.currentTimeMillis)
        transactionQueue = List.empty // Clear the transaction queue after mining

        val newBlock = ProofOfWork.generateProof(transactions, blockchain.head.hash, System.currentTimeMillis)
        blockchain = newBlock :: blockchain

        context.log.info(s"Mined new block: ${newBlock.hash}, Nonce: ${newBlock.nonce}")
        context.system.eventStream ! EventStream.Publish(BlockMined(newBlock))
        Behaviors.same

      case MineBlock =>
        context.log.info("No transactions to mine.")
        Behaviors.same

      case GetBlockchain(replyTo) =>
        replyTo ! BlockchainState(blockchain)
        Behaviors.same
    }
  }
}
