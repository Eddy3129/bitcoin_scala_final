// src/main/scala/blockchain/ProofOfWork.scala

package blockchain

import blockchain.model.{Block, LinkedBlock, Transactions}
import blockchain.util.Util
import java.security.MessageDigest

object ProofOfWork {
  val difficulty: Int = 4 // Adjust difficulty as needed

  def generateProof(transactions: Transactions, hashPrev: String, timestamp: Long): LinkedBlock = {
    var nonce = 0L
    var hash = calculateHash(transactions, hashPrev, timestamp, nonce)

    while (!hash.startsWith("0" * difficulty)) {
      nonce += 1
      hash = calculateHash(transactions, hashPrev, timestamp, nonce)
      if (nonce % 100000 == 0) {
        println(s"Trying nonce: $nonce, current hash: $hash")
      }
    }

    LinkedBlock(hash, hashPrev, transactions, timestamp, difficulty, nonce)
  }

  def calculateHash(transactions: Transactions, hashPrev: String, timestamp: Long, nonce: Long): String = {
    val data = s"$hashPrev-${transactions.id}-$timestamp-$nonce"
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(data.getBytes("UTF-8"))
    hashBytes.map("%02x".format(_)).mkString // Convert hash bytes to hexadecimal string
  }
}
