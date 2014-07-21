package org.overviewproject.models

import java.math.BigInteger
import java.security.SecureRandom

import org.overviewproject.tree.orm.DocumentSetComponent

case class ApiToken(
  token: String,
  createdAt: java.sql.Timestamp, // Squeryl drops time when we use Date
  createdBy: String,
  description: String,
  documentSetId: Long
) extends DocumentSetComponent

object ApiToken {
  private object TokenGenerator {
    private val secureRandom = new SecureRandom()

    /*
     * How many bits?
     *
     * Look at the table at https://en.wikipedia.org/wiki/Birthday_attack#Mathematics
     *
     * We want to support 10**10 tokens with 10^-15 possibility of error.
     */
    private val TokenLengthInBits = 128

    /*
     * What level of encoding? base-64? base-32? etc.
     *
     * base-64 would be nice, but Java makes base-32 _so_ much easier. And it
     * barely makes the string any longer.
     */
    private val Radix = scala.math.min(Character.MAX_RADIX, 36)

    /*
     * Track how many we've generated. We'll need to reseed from system entropy
     * once in a while so that an attacker can't figure out our seed and then
     * predict future API keys.
     */
    private var nGenerated = 0L
    private val NToGenerateUntilReseed = 0xfffL // no idea what a good magic number is here
    private val SeedBytes = 20 // no idea what a good magic number is here

    def reseed: Unit = {
      secureRandom.setSeed(secureRandom.generateSeed(SeedBytes))
    }

    def next: String = {
      if ((nGenerated & NToGenerateUntilReseed) == NToGenerateUntilReseed) {
        reseed
      }
      nGenerated += 1
      new BigInteger(TokenLengthInBits, secureRandom).toString(36)
    }
  }

  def generate(email: String, documentSetId: Long, description: String) : ApiToken = {
    ApiToken(
      token=TokenGenerator.next,
      createdBy=email,
      createdAt=new java.sql.Timestamp(System.currentTimeMillis()),
      documentSetId=documentSetId,
      description=description
    )
  }
}
