package blockchain.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import blockchain.model.{Block, LinkedBlock}

object ConsensusActor {
  sealed trait Command
  case class ProposeBlock(block: LinkedBlock, proposer: ActorRef[Command]) extends Command
  case class ValidateBlock(block: LinkedBlock, validator: ActorRef[Command]) extends Command
  case class BlockValidated(block: LinkedBlock) extends Command
  case class ConsensusAchieved(block: LinkedBlock) extends Command

  def apply(blockchainer: ActorRef[Blockchainer.Command]): Behavior[Command] = Behaviors.setup { context =>
    var proposedBlocks: Map[String, Int] = Map.empty // Block hash -> vote count
    val requiredVotes = 2 // Simplified threshold

    Behaviors.receiveMessage {
      case ProposeBlock(block, proposer) =>
        proposedBlocks += (block.hash -> (proposedBlocks.getOrElse(block.hash, 0) + 1))
        context.log.info(s"Block proposed: ${block.hash}, Votes: ${proposedBlocks(block.hash)}")
        if (proposedBlocks(block.hash) >= requiredVotes) {
          context.log.info(s"Consensus achieved for block: ${block.hash}")
          blockchainer ! Blockchainer.BlockMined(block)
          context.self ! ConsensusAchieved(block)
        }
        Behaviors.same

      case ValidateBlock(block, validator) =>
        context.self ! ProposeBlock(block, validator)
        Behaviors.same

      case ConsensusAchieved(block) =>
        context.log.info(s"Block added to blockchain: ${block.hash}")
        Behaviors.same
    }
  }
}
