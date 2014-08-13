package models

object UserRole extends Enumeration {
  type UserRole = Value

  val NormalUser = Value(1, "NormalUser")
  val Administrator = Value(2, "Administrator")
}
