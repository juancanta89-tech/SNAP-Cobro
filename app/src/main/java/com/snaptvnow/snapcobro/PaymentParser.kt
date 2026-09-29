package com.snaptvnow.snapcobro

data class Payment(val payer:String,val amount:Double,val xuiUser:String,val months:Int)
object PaymentParser {
 private val pattern=Regex("""BofA:\s*(.+?)\s+sent you \$(\d+(?:\.\d{2})?)\s+for\s+["“]([^"”]+)["”]""",RegexOption.IGNORE_CASE)
 private val plans=mapOf(12.0 to 1,35.0 to 3,66.0 to 6,125.0 to 12)
 fun parse(text:String):Payment? { val m=pattern.find(text)?:return null; val amount=m.groupValues[2].toDoubleOrNull()?:return null; val months=plans[amount]?:return null; return Payment(m.groupValues[1].trim(),amount,m.groupValues[3].trim(),months) }
}
