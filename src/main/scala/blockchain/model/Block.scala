// src/main/scala/blockchain/model/Block.scala

package blockchain.model

sealed trait Block {
  def hash: String
  def hashPrev: String
  def timestamp: Long
  def transactions: Transactions
  def difficulty: Int
  def nonce: Long
}

case object RootBlock extends Block {
  val hash = "root"
  val hashPrev = ""
  val timestamp = 0L
  val transactions = Transactions(List.empty, 0L)
  val difficulty = 0
  val nonce = 0L
}

case class LinkedBlock(
                        hash: String,
                        hashPrev: String,
                        transactions: Transactions,
                        timestamp: Long,
                        difficulty: Int,
                        nonce: Long
                      ) extends Block
