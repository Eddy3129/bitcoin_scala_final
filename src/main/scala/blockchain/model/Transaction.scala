// src/main/scala/blockchain/model/Transaction.scala

package blockchain.model

import blockchain.util.Util

trait JsonSerializable // Marker trait for serialization

case class TransactionItem(sender: String, recipient: String, amount: Double, timestamp: Long) extends JsonSerializable {
  def id: String = s"$sender-$recipient-$amount-$timestamp"
}

case class Transactions(items: List[TransactionItem], timestamp: Long) extends JsonSerializable {
  def id: String = s"${items.hashCode()}-$timestamp"
  def hasValidItems: Boolean = items.nonEmpty && items.forall(_.amount > 0)
}
