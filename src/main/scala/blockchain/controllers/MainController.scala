// src/main/scala/blockchain/controllers/MainController.scala

package blockchain.controllers

import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import blockchain.actors.{Blockchainer, Peer}
import blockchain.model.TransactionItem
import scalafx.application.Platform
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

  // List of ActorSystems representing different peers (if needed)
  private var actorSystems: List[ActorSystem[Nothing]] = List.empty

  // List of Peer actors
  private var peerActors: List[ActorRef[Peer.Command]] = List.empty

  // List of existing Blockchainer actors
  private var blockchainerActors: List[ActorRef[Blockchainer.Command]] = List.empty

  // Map to keep track of account balances
  private val accountBalances: mutable.Map[String, Double] = mutable.Map.empty

  // Data for the blockchain table
  private val blockDetailsData: ObservableBuffer[(String, String, String)] = ObservableBuffer()

  /**
   * Initialize the controller with existing ActorSystems and their corresponding Blockchainer actors.
   *
   * @param systems           List of ActorSystems
   * @param blockchainerRefs  List of existing Blockchainer actor references
   * @param peerRefs          List of existing Peer actor references
   */
  def setActorSystems(systems: List[ActorSystem[Nothing]],
                      blockchainerRefs: List[ActorRef[Blockchainer.Command]],
                      peerRefs: List[ActorRef[Peer.Command]]): Unit = {
    actorSystems = systems
    blockchainerActors = blockchainerRefs
    peerActors = peerRefs

    // Setup UI bindings
    setupUI()

    // Subscribe to BlockMined events from all ActorSystems
    actorSystems.foreach { system =>
      // Spawn a listener actor for BlockMined events
      val listener: ActorRef[Blockchainer.BlockMined] =
        system.systemActorOf(
          Behaviors.receiveMessage[Blockchainer.BlockMined] { message =>
            Platform.runLater {
              // Update block details in the UI
              val transactionsDetails = message.block.transactions.items.map { t =>
                s"${t.sender} -> ${t.recipient}: ${t.amount}"
              }.mkString("; ")
              val miner = "System" // Adjust if miner information is available
              val blockEntry = (message.block.hash, transactionsDetails, s"Miner: $miner")
              blockDetailsData += blockEntry

              // Log mining details
              miningListView.getItems.add(s"Mined block: ${message.block.hash}")
              updateAccountBalances()
            }
            Behaviors.same
          },
          s"BlockMinedListener-${system.name}"
        )

      // Subscribe the listener to BlockMined events
      system.eventStream ! EventStream.Subscribe(listener)
    }
  }

  // Initialize UI components
  private def setupUI(): Unit = {
    blockHashColumn.cellValueFactory = cellData => scalafx.beans.property.StringProperty(cellData.value._1)
    transactionsColumn.cellValueFactory = cellData => scalafx.beans.property.StringProperty(cellData.value._2)
    minerColumn.cellValueFactory = cellData => scalafx.beans.property.StringProperty(cellData.value._3)
    blockDetailsTable.items = blockDetailsData
  }

  // Handle Create Account action
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

  // Handle Add Transaction action
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

    // Update balances
    accountBalances(sender) -= amount
    accountBalances(recipient) += amount
    val transaction = TransactionItem(sender, recipient, amount, System.currentTimeMillis())

    // Broadcast transaction to all peers
    peerActors.foreach { peer =>
      println(s"Sending transaction to peer: ${peer.path.name}")
      peer ! Peer.BroadcastTransaction(transaction)
    }

    Platform.runLater {
      miningListView.getItems.add(s"Transaction added: $sender -> $recipient: $amount")
      updateAccountBalances()
      amountField.clear() // Clear the amount field after transaction is added
    }
  }

  // Handle Mine Block action
  def handleMineBlock(event: ActionEvent): Unit = {
    Platform.runLater {
      miningListView.getItems.add("Mining block...")
    }

    // Trigger mining on all existing Blockchainer actors
    blockchainerActors.foreach { blockchainer =>
      blockchainer ! Blockchainer.MineBlock
    }
  }

  // Update the account balances in the UI
  private def updateAccountBalances(): Unit = {
    val updatedAccounts = accountBalances.map { case (user, balance) =>
      f"$user (Balance: $balance%.2f)"
    }.toList
    accountsListView.getItems.setAll(updatedAccounts: _*)
  }

  // Initialize peers with their configurations (if needed)
  def initializePeers(peerConfigs: List[(String, Int)]): Unit = {
    println(s"Initialized ${peerConfigs.size} peers.")
    // Implement peer initialization if required
  }
}
