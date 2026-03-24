package net.spartanb312.grunteon.obfuscator.util

import net.spartanb312.grunteon.obfuscator.util.logging.ILogger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import java.text.SimpleDateFormat
import java.util.Date

object Logger : ILogger by SimpleLogger(
    "Grunteon",
    "logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
)