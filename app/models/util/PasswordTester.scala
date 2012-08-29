package models.util

import scala.util.matching.Regex

class PasswordTester(val password: String) {
  private class RichString(s: String) {
    def tr(a: String, b: String) = {
      s.map({ c =>
        val i = a.indexOf(c)
        if (i == -1) c else b(i)
      })
    }

    def normalize = {
      s.toLowerCase.tr("13456890@#$(+[{<|", "ieasgggoahsctccci")
    }
  }
  private implicit def stringToRichString(s: String) : RichString = new RichString(s)

  // Copied from howsecureismypassword.net
  val Dictionary : Set[String] = """password1 fuck_inside deepthroat qwertyuiop
    asdfghjkl spiderman basketball webmaster chocolate alexander beautiful 
    swordfish elizabeth masterbate penetration christine wolverine 
    masterbating unbelievable intercourse squerting insertion temptress 
    celebrity interacial streaming pertinant fantasies ejaculation 
    businessbabe experience contortionist cheerleaers christian housewifes 
    seductive gangbanged experienced passwords transexual gallaries 
    lockerroom absolutely masterbaiting housewife masturbation pornographic 
    thumbnils knickerless underwear enterprise scandinavian techniques 
    manchester penetrating butterfly earthlink girfriend uncencored gymnastic 
    hollywood insertions wonderboy skywalker fuckinside ursitesux stonecold 
    christina stephanie password2 quant4307s iloveyou1 sebastian jamesbond 
    iloveyou2 amsterdam catherine football1 charlotte christopher september 
    123123123 qwerty123 southpark california tottenham barcelona katherine 
    stoppedby christmas cocksucker birthday4 something starcraft godfather 
    password9 favorite6 paintball wrestling gladiator washington lightning 
    postov1000 chevrolet snowboard birthday1 australia charlie123 outoutout 
    superstar pinkfloyd hurricane idontknow qazwsxedc baseball1 favorite2 
    alexandra primetime21 blackjack cleveland sexsexsex letmein22 fatluvr69 
    dragonball slimed123 scoobydoo highlander playstation gnasher23 porn4life 
    excalibur wednesday sweetness undertaker university moonlight president 
    newcastle 1q2w3e4r5t pimpdaddy panasonic motherfucker peternorth 
    cardinals fortune12 
    """.split("""\s+""").map(_.normalize).toSet

  // 2011's fast GPUs can manage 5 billion passwords per second
  // 8-char, mixed-case alphanumeric passwords aren't secure, but we need to
  // support them anyway.
  val NumGuessesForSafePassword = BigInt(5000000000L) * 12 * 3600

  val Matchers : Seq[(String,String,Int)] = Seq(
    ("ASCII lowercase", """[a-z]""", 26),
    ("ASCII uppercase", """[A-Z]""", 26),
    ("ASCII numbers", """[0-9]""", 10),
    ("ASCII space", """ """, 1),
    ("ASCII top row symbols", """[!@Â£#\$%\^&\*\(\)\-_=\+]""", 15),
    ("ASCII other symbols", """[\?\/\.>\,<`~\\|"';:\]\}\[\{]""", 18),
    ("Unicode Latin 1 Supplement", """[ -ÿ]""", 94),
    ("Unicode Latin Extended A", """[Ā-ſ]""", 128),
    ("Unicode Latin Extended B", """[ƀ-ɏ]""", 208),
    ("Unicode Latin Extended C", """[Ⱡ-Ɀ]""", 32),
    ("Unicode Latin Extended D", """[꜠-ꟿ]""", 29),
    ("Unicode Cyrillic Uppercase", """[А-Я]""", 32),
    ("Unicode Cyrillic Lowercase", """[а-я]""", 32)
  )

  def isInDictionary = {
    Dictionary.contains(password.normalize)
  }

  def countCharacterSpace = {
    Matchers.map((t) => if (password.matches(".*" + t._2 + ".*")) t._3 else 0).reduce(_ + _)
  }

  def numGuessesNeeded : BigInt = {
    if (isInDictionary) {
      BigInt(Dictionary.size)
    } else {
      BigInt(countCharacterSpace).pow(password.length)
    }
  }

  def isSecure = {
    numGuessesNeeded > NumGuessesForSafePassword
  }
}
