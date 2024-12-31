package blockchain.model

case class TransactionItem(sender: String, recipient: String, amount: Double, timestamp: Long) {
  def id: String = s"$sender-$recipient-$amount-$timestamp"
}

case class Transactions(items: List[TransactionItem], timestamp: Long) {
  def id: String = s"${items.hashCode()}-$timestamp"
  def hasValidItems: Boolean = items.nonEmpty && items.forall(_.amount > 0)
}
