package blockchain.controllers

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.Behaviors
import blockchain.actors.Blockchainer
import blockchain.Peer
import blockchain.model.TransactionItem
import scalafx.application.Platform
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafxml.core.macros.sfxml

import scala.collection.mutable

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
                      private val minerColumn: TableColumn[(String, String, String), String]
                    ) {

  private var blockchainerActor: ActorRef[Blockchainer.Command] = _
  private val accountBalances: mutable.Map[String, Double] = mutable.Map.empty
  private val blockDetailsData: ObservableBuffer[(String, String, String)] = ObservableBuffer()

  def setActorSystem(system: ActorSystem[Nothing]): Unit = {
    blockchainerActor = system.systemActorOf(Blockchainer(), "Blockchainer")
    blockHashColumn.cellValueFactory = cellData => StringProperty(cellData.value._1)
    transactionsColumn.cellValueFactory = cellData => StringProperty(cellData.value._2)
    minerColumn.cellValueFactory = cellData => StringProperty(cellData.value._3)
    blockDetailsTable.items = blockDetailsData

    // Listen for mined block events
    val blockListener = system.systemActorOf(
      Behaviors.receiveMessage[Blockchainer.BlockMined] { message =>
        Platform.runLater {
          // Add block details to table
          val transactionsDetails = message.block.transactions.items.map { t =>
            s"${t.sender} -> ${t.recipient}: ${t.amount}"
          }.mkString("; ")
          val blockEntry = (message.block.hash, transactionsDetails, "Miner: System")
          blockDetailsData += blockEntry

          // Log mining details
          miningListView.getItems.add(s"Mined block: ${message.block.hash}")

          // Update account balances in the UI
          updateAccountBalances()
        }
        Behaviors.same
      },
      "BlockListener"
    )

    system.eventStream ! EventStream.Subscribe(blockListener)
  }

  def handleCreateAccount(event: ActionEvent): Unit = {
    val username = usernameField.getText.trim
    if (username.nonEmpty && !accountBalances.contains(username)) {
      accountBalances(username) = 100.0
      Platform.runLater {
        accountsListView.getItems.add(s"$username (Balance: 100.0)")
        senderComboBox.getItems.add(username)
        recipientComboBox.getItems.add(username)
        usernameField.clear() // Clear the username field after account creation
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
        miningListView.getItems.add(s"Error: Invalid transaction details")
      }
      return
    }

    if (!accountBalances.contains(sender) || !accountBalances.contains(recipient)) {
      Platform.runLater {
        miningListView.getItems.add(s"Error: Sender or recipient does not exist")
      }
      return
    }

    if (accountBalances(sender) < amount) {
      Platform.runLater {
        miningListView.getItems.add(s"Error: Insufficient balance for $sender")
      }
      return
    }

    accountBalances(sender) -= amount
    accountBalances(recipient) += amount
    val transaction = TransactionItem(sender, recipient, amount, System.currentTimeMillis())
    blockchainerActor ! Blockchainer.AddTransaction(transaction)

    Platform.runLater {
      miningListView.getItems.add(s"Transaction added: $sender -> $recipient: $amount")
      updateAccountBalances()
      amountField.clear() // Clear the amount field after transaction is added
    }
  }

  def handleMineBlock(event: ActionEvent): Unit = {
    blockchainerActor ! Blockchainer.MineBlock
    Platform.runLater {
      miningListView.getItems.add("Mining block...")
    }
  }

  private def updateAccountBalances(): Unit = {
    val updatedAccounts = accountBalances.map { case (user, balance) =>
      s"$user (Balance: $balance)"
    }.toList
    accountsListView.getItems.setAll(updatedAccounts: _*)
  }
}
