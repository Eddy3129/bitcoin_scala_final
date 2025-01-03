package blockchain.controllers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import blockchain.actors.{Blockchainer, Peer}
import blockchain.model.{Block, LinkedBlock, TransactionItem}
import blockchain.util.Util
import scalafx.application.Platform
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafxml.core.macros.sfxml
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.duration._

@sfxml
class MainController(
                      private val usernameField: TextField,
                      private val senderComboBox: ComboBox[String],
                      private val recipientComboBox: ComboBox[String],
                      private val amountField: TextField,
                      private val accountsListView: ListView[String],
                      private val miningListView: ListView[String],
                      private val blockchainListView: ListView[String],
                      private val mempoolListView: ListView[String],
                      private val validationListView: ListView[String],
                      private val statusLabel: Label
                    ) {

  private var peerActors: List[ActorRef[Peer.Command]] = List.empty
  private var blockchainerActors: List[ActorRef[Blockchainer.Command]] = List.empty
  private var actorSystem: Option[ActorSystem[_]] = None
  private var isMining = false

  private val mempool = ObservableBuffer[TransactionItem]()
  private val mempoolView = ObservableBuffer[String]()
  private val blockchain = ObservableBuffer[String]()
  private val accounts = ObservableBuffer[String]()
  private val miningLogs = ObservableBuffer[String]()
  private val validationLogs = ObservableBuffer[String]()
  private val accountBalances = scala.collection.mutable.Map[String, Double]()
  private val processedTransactions = scala.collection.mutable.Set[String]()

  private val senderItems = ObservableBuffer[String]()
  private val recipientItems = ObservableBuffer[String]()
  private val permanentAccounts = scala.collection.mutable.Set[String]()  // Add this line

  def setActorSystems(peerRefs: List[ActorRef[Peer.Command]],
                      blockchainerRefs: List[ActorRef[Blockchainer.Command]],
                      system: ActorSystem[_]): Unit = {
    peerActors = peerRefs
    blockchainerActors = blockchainerRefs
    actorSystem = Some(system)
    setupUI()

    val transactionSubscriber = system.systemActorOf(
      Behaviors.receiveMessage[Blockchainer.TransactionReceived] { msg =>
        Platform.runLater {
          updateTransactions(msg.transaction)
        }
        Behaviors.same
      },
      "transaction-subscriber"
    )

    // Subscribe without specifying a class (the actor inherently knows what it listens for)
    system.eventStream.tell(akka.actor.typed.eventstream.EventStream.Subscribe(transactionSubscriber))

    actorSystem.foreach { sys =>
      implicit val executionContext = sys.executionContext
      sys.scheduler.scheduleWithFixedDelay(0.seconds, 1.second)(() => Platform.runLater(refreshUI()))
    }
  }

  private def setupUI(): Unit = {
    blockchainListView.items = blockchain
    mempoolListView.items = mempoolView
    accountsListView.items = accounts
    miningListView.items = miningLogs
    validationListView.items = validationLogs

    senderComboBox.items = senderItems
    recipientComboBox.items = recipientItems

    List(blockchainListView, mempoolListView, accountsListView, miningListView, validationListView)
      .foreach(_.styleClass += "list-view-large")

    List(senderComboBox, recipientComboBox)
      .foreach(_.styleClass += "combo-box-large")

    List(usernameField, amountField)
      .foreach(_.styleClass += "text-field-large")

    // Initialize with Genesis account
    addAccount("Genesis", 1000.0)
    permanentAccounts.add("Genesis")  // Add Genesis to permanent accounts
    updateStatus("Ready")
  }

  private def refreshUI(): Unit = {
    blockchainerActors.foreach { blockchainer =>
      actorSystem.foreach { system =>
        val responseHandler = system.systemActorOf(
          Behaviors.receiveMessage[Blockchainer.BlockchainState] { state =>
            Platform.runLater {
              val currentSize = blockchain.size
              blockchain.clear()
              updateBlockchainView(state.blocks)
              updateMempoolAfterBlockchain(state.blocks)

              // Update mining status
              if (isMining && !state.isMining) {
                isMining = false
                if (currentSize != state.blocks.size) {
                  // New block was mined
                  addMiningLog("[Mining] Mining completed successfully")
                  addValidationLog("[Validation] New block validation complete")
                  updateStatus("Mining completed - New block added")
                } else {
                  updateStatus("Ready")
                }
              } else if (isMining && state.isMining) {
                updateStatus(s"Mining in progress... (${mempool.size} transactions)")
              } else if (!isMining) {
                updateStatus("Ready")
              }

              // Only recalculate balances if blockchain changed
              if (currentSize != state.blocks.size) {
                recalculateAccountBalances(state.blocks)
              }
            }
            Behaviors.stopped
          },
          s"blockchain-state-handler-${java.util.UUID.randomUUID()}"
        )
        blockchainer ! Blockchainer.GetBlockchain(responseHandler)
      }
    }
  }

  // Modify recalculateAccountBalances
  private def recalculateAccountBalances(blocks: List[Block]): Unit = {
    // Initialize all accounts with their starting balances
    val tempBalances = scala.collection.mutable.Map[String, Double]()

    // Initialize all known accounts with their starting balances
    permanentAccounts.foreach { acc =>
      tempBalances(acc) = if (acc == "Genesis") 1000.0 else 100.0
    }

    // Process all transactions in chronological order
    blocks.reverse.foreach {
      case b: LinkedBlock =>
        b.transactions.items.foreach { tx =>
          // Ensure accounts exist before processing
          if (!tempBalances.contains(tx.sender)) tempBalances(tx.sender) = 100.0
          if (!tempBalances.contains(tx.recipient)) tempBalances(tx.recipient) = 100.0

          tempBalances(tx.sender) = tempBalances(tx.sender) - tx.amount
          tempBalances(tx.recipient) = tempBalances(tx.recipient) + tx.amount
        }
      case _ => // Skip root block
    }

    // Update the actual balances
    accountBalances.clear()
    tempBalances.foreach { case (acc, balance) =>
      accountBalances(acc) = balance
      if (!permanentAccounts.contains(acc)) {
        permanentAccounts.add(acc)
      }
    }

    updateAccountsView()
  }

  private def updateBlockchainView(blocks: List[Block]): Unit = {
    blockchain.clear()
    blocks.reverse.foreach {
      case b: LinkedBlock =>
        val blockEntry = formatBlockEntry(b)
        blockchain.add(blockEntry)
        b.transactions.items.foreach(processTransaction)
      case root => blockchain.add(s"Root Block: ${root.hash}")
    }
  }

  private def updateMempoolAfterBlockchain(blocks: List[Block]): Unit = {
    val minedTransactionIds = blocks.flatMap {
      case b: LinkedBlock => b.transactions.items.map(_.id)
      case _ => Nil
    }.toSet

    mempool.removeAll(mempool.filter(tx => minedTransactionIds.contains(tx.id)))
    updateMempoolView()
  }

  private def updateAccountBalances(blocks: List[Block]): Unit = {
    accountBalances.clear()
    addAccount("Genesis", 1000.0) // Reset Genesis account

    blocks.reverse.foreach {
      case b: LinkedBlock =>
        b.transactions.items.foreach { tx =>
          accountBalances(tx.sender) = accountBalances.getOrElse(tx.sender, 0.0) - tx.amount
          accountBalances(tx.recipient) = accountBalances.getOrElse(tx.recipient, 0.0) + tx.amount
        }
      case _ => // Skip root block
    }

    updateAccountsView()
  }

  private def addAccount(username: String, initialBalance: Double): Unit = {
    if (!accountBalances.contains(username)) {
      accountBalances(username) = initialBalance
      permanentAccounts.add(username)  // Add to permanent accounts
      updateAccountsView()

      // Update combo boxes
      if (!senderItems.contains(username)) senderItems.add(username)
      if (!recipientItems.contains(username)) recipientItems.add(username)
    }
  }

  private def updateAccountsView(): Unit = {
    accounts.clear()
    accountBalances.foreach { case (name, balance) =>
      accounts.add(f"$name (Balance: $balance%.2f BTC)")
    }
  }

  private def processTransaction(tx: TransactionItem): Unit = {
    if (!processedTransactions.contains(tx.id)) {
      processedTransactions.add(tx.id)
      addAccount(tx.sender, 0.0)
      addAccount(tx.recipient, 0.0)
    }
  }

  def handleCreateAccount(event: ActionEvent): Unit = {
    val username = usernameField.getText.trim
    if (username.nonEmpty && !accountBalances.contains(username)) {
      addAccount(username, 100.0)
      usernameField.clear()
      updateStatus(s"Account created: $username")
      addMiningLog(s"New account created: $username with 100 BTC")
    } else {
      addMiningLog("Error: Invalid or duplicate username")
      updateStatus("Account creation failed")
    }
  }

  def handleAddTransaction(event: ActionEvent): Unit = {
    val sender = Option(senderComboBox.getValue).getOrElse("")
    val recipient = Option(recipientComboBox.getValue).getOrElse("")
    val amount = try amountField.getText.toDouble catch {
      case _: NumberFormatException => 0.0
    }

    if (validateTransaction(sender, recipient, amount)) {
      ensureAccountExists(sender)
      ensureAccountExists(recipient)

      val transaction = TransactionItem(sender, recipient, amount, System.currentTimeMillis())

      // Add to local mempool first
      mempool.add(transaction)
      updateMempoolView()
      processTransaction(transaction)

      // Then broadcast to peers
      peerActors.foreach(_ ! Peer.BroadcastTransaction(transaction))

      amountField.clear()
      updateStatus(s"Transaction added: $sender -> $recipient: $amount BTC")
    }
  }

  private def validateTransaction(sender: String, recipient: String, amount: Double): Boolean = {
    if (sender.isEmpty || recipient.isEmpty || amount <= 0) {
      addMiningLog("Error: Invalid transaction details")
      updateStatus("Transaction failed: Invalid details")
      return false
    }

    val senderBalance = accountBalances.getOrElse(sender, 0.0)
    if (senderBalance < amount) {
      addMiningLog(s"Error: Insufficient funds. $sender has only $senderBalance BTC")
      updateStatus("Transaction failed: Insufficient funds")
      return false
    }

    true
  }

  def handleMineBlock(event: ActionEvent): Unit = {
    if (mempool.isEmpty) {
      addMiningLog("No transactions to mine")
      updateStatus("Mining failed: Empty mempool")
      return
    }

    if (!isMining) {
      isMining = true
      addMiningLog("[Mining] Starting mining process...")
      addValidationLog("[Mining] Initiating new block mining")
      updateStatus(s"Mining in progress... (${mempool.size} transactions)")
      blockchainerActors.foreach(_ ! Blockchainer.MineBlock)
    }
  }

  def updateTransactions(transaction: TransactionItem): Unit = {
    if (!processedTransactions.contains(transaction.id) &&
      !mempool.exists(_.id == transaction.id)) {  // Add this check
      Platform.runLater {
        mempool.add(transaction)
        updateMempoolView()
        addMiningLog(s"New transaction received: ${formatTransaction(transaction)}")
        processTransaction(transaction)
      }
    }
  }

  private def updateMempoolView(): Unit = {
    mempoolView.clear()
    mempool.foreach(tx => mempoolView.add(formatTransaction(tx)))
  }

  def updateBlocks(block: LinkedBlock): Unit = {
    Platform.runLater {
      if (!blockchain.exists(_.contains(block.hash))) {
        isMining = false // Reset mining state
        blockchain.insert(0, formatBlockEntry(block))

        // Process all accounts in the block first
        block.transactions.items.foreach { tx =>
          ensureAccountExists(tx.sender)
          ensureAccountExists(tx.recipient)
        }

        // Remove mined transactions from mempool
        block.transactions.items.foreach { tx =>
          mempool.removeIf(_.id == tx.id)
        }
        updateMempoolView()

        addMiningLog(s"[Block] New block mined: ${block.hash}")
        addValidationLog(s"[Validation] Block validated successfully")
        addValidationLog(s"[Validation] Hash: ${block.hash}")
        addValidationLog(s"[Validation] Previous hash: ${block.hashPrev}")
        addValidationLog(s"[Validation] Nonce: ${block.nonce}")
        block.transactions.items.foreach { tx =>
          addValidationLog(s"[Validation] Transaction: ${formatTransaction(tx)}")
        }

        // Update status to show mining is complete
        updateStatus(s"Mining completed - New block added: ${block.hash.take(8)}...")
        refreshUI()
      } else {
        // Update status even for duplicate blocks
        updateStatus("Ready")
      }
    }
  }

  private def ensureAccountExists(username: String): Unit = {
    if (!accountBalances.contains(username) && !permanentAccounts.contains(username)) {
      accountBalances(username) = 100.0 // Initialize with 100 BTC
      permanentAccounts.add(username)

      // Update combo boxes
      if (!senderItems.contains(username)) senderItems.add(username)
      if (!recipientItems.contains(username)) recipientItems.add(username)
    }
  }

  private def formatTransaction(tx: TransactionItem): String =
    f"${tx.sender} -> ${tx.recipient}: ${tx.amount}%.2f BTC (${Util.timestampToDateTime(tx.timestamp)})"

  private def formatBlockEntry(block: LinkedBlock): String = {
    val transactionsDetails = block.transactions.items.map(formatTransaction).mkString("\n  ")
    s"""Block Hash: ${block.hash}
       |Previous Hash: ${block.hashPrev}
       |Transactions:
       |  $transactionsDetails
       |Nonce: ${block.nonce}
       |Timestamp: ${Util.timestampToDateTime(block.timestamp)}""".stripMargin
  }

  private def addMiningLog(message: String): Unit = {
    Platform.runLater {
      miningLogs += s"[${Util.timestampToDateTime(System.currentTimeMillis)}] $message"
      if (miningLogs.size > 100) miningLogs.remove(0)
      miningListView.scrollTo(miningLogs.size - 1)
    }
  }

  private def addValidationLog(message: String): Unit = {
    Platform.runLater {
      validationLogs += s"[${Util.timestampToDateTime(System.currentTimeMillis)}] $message"
      if (validationLogs.size > 100) validationLogs.remove(0)
      validationListView.scrollTo(validationLogs.size - 1)
    }
  }

  private def updateStatus(message: String): Unit = {
    Platform.runLater {
      statusLabel.text = s"Status: $message"
    }
  }
}