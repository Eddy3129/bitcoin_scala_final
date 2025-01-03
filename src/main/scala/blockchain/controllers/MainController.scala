package blockchain.controllers

import akka.actor.typed.ActorRef
import blockchain.actors.{Blockchainer, Peer}
import blockchain.model.{LinkedBlock, TransactionItem}
import scalafx.application.Platform
import scalafx.collections.{ObservableBuffer, ObservableHashSet}
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafxml.core.macros.sfxml

@sfxml
class MainController(
                      private val usernameField: TextField,
                      private val senderComboBox: ComboBox[String],
                      private val recipientComboBox: ComboBox[String],
                      private val amountField: TextField,
                      private val accountsListView: ListView[String],
                      private val miningListView: ListView[String],
                      private val blockDetailsTable: TableView[(String, String, String)],
                      private val blockHashColumn: TableColumn[(String, String, String), String],
                      private val transactionsColumn: TableColumn[(String, String, String), String],
                      private val minerColumn: TableColumn[(String, String, String), String],
                      private val mempoolListView: ListView[String],
                      private val validationListView: ListView[String]
                    ) {

  private var peerActors: List[ActorRef[Peer.Command]] = List.empty
  private var blockchainerActors: List[ActorRef[Blockchainer.Command]] = List.empty

  // Shared observable state
  private val transactions: ObservableHashSet[String] = new ObservableHashSet[String]()
  private val blocks: ObservableBuffer[(String, String, String)] = ObservableBuffer()
  private val validationLogs: ObservableBuffer[String] = ObservableBuffer()
  private val accountBalances = scala.collection.mutable.Map[String, Double]()

  def setActorSystems(peerRefs: List[ActorRef[Peer.Command]], blockchainerRefs: List[ActorRef[Blockchainer.Command]]): Unit = {
    peerActors = peerRefs
    blockchainerActors = blockchainerRefs

    setupUI()

    // Observe changes to shared state
    transactions.onChange { (_, _) =>
      Platform.runLater {
        mempoolListView.getItems.setAll(transactions.toSeq: _*)
      }
    }

    blocks.onChange { (_, _) =>
      Platform.runLater {
        blockDetailsTable.getItems.setAll(blocks)
      }
    }

    validationLogs.onChange { (_, _) =>
      Platform.runLater {
        validationListView.getItems.setAll(validationLogs)
      }
    }
  }

  private def setupUI(): Unit = {
    blockHashColumn.cellValueFactory = cellData => scalafx.beans.property.StringProperty(cellData.value._1)
    transactionsColumn.cellValueFactory = cellData => scalafx.beans.property.StringProperty(cellData.value._2)
    minerColumn.cellValueFactory = cellData => scalafx.beans.property.StringProperty(cellData.value._3)
    blockDetailsTable.items = blocks
  }

  def handleCreateAccount(event: ActionEvent): Unit = {
    val username = usernameField.getText.trim
    if (username.nonEmpty && !accountBalances.contains(username)) {
      accountBalances(username) = 100.0
      Platform.runLater {
        accountsListView.getItems.add(s"$username (Balance: 100 BTC)")
        senderComboBox.getItems.add(username)
        recipientComboBox.getItems.add(username)
        usernameField.clear()
      }
    } else {
      Platform.runLater {
        miningListView.getItems.add(s"Error: Invalid or duplicate username")
      }
    }
  }


  def handleAddTransaction(event: ActionEvent): Unit = {
    val sender = Option(senderComboBox.getValue).getOrElse("")
    val recipient = Option(recipientComboBox.getValue).getOrElse("")
    val amount = try amountField.getText.toDouble catch {
      case _: NumberFormatException => 0.0
    }

    if (sender.isEmpty || recipient.isEmpty || amount <= 0) {
      Platform.runLater {
        miningListView.getItems.add("Error: Invalid transaction details")
      }
      return
    }

    val transaction = TransactionItem(sender, recipient, amount, System.currentTimeMillis())
    peerActors.foreach { peer =>
      peer ! Peer.BroadcastTransaction(transaction)
    }
    Platform.runLater {
      miningListView.getItems.add(s"Transaction added: $sender -> $recipient: $amount")
    }
  }

  def handleMineBlock(event: ActionEvent): Unit = {
    Platform.runLater {
      miningListView.getItems.add("Mining block...")
    }
    blockchainerActors.foreach { blockchainer =>
      blockchainer ! Blockchainer.MineBlock
    }
  }

  def updateTransactions(transaction: TransactionItem): Unit = {
    val transactionDetails = s"${transaction.sender} -> ${transaction.recipient}: ${transaction.amount}"
    if (!transactions.contains(transactionDetails)) {
      transactions += transactionDetails
    }
  }

  def updateBlocks(block: LinkedBlock): Unit = {
    val transactionsDetails = block.transactions.items.map(t => s"${t.sender} -> ${t.recipient}: ${t.amount}").mkString("; ")
    val blockEntry = (block.hash, transactionsDetails, "Node")
    if (!blocks.exists(_._1 == block.hash)) {
      blocks += blockEntry
    }
  }

  def updateValidationLog(log: String): Unit = {
    validationLogs += log
  }
}
