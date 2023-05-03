package io.rhonix.shared

object TerminalMode {
  def readMode: Boolean = System.console() != null
}
