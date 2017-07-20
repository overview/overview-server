package models

case class PotentialNewUser(
  email: String,
  password: String,
  emailSubscriber: Boolean
)
