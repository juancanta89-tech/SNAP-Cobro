package com.snaptvnow.snapcobro
import android.content.Context
import java.security.MessageDigest
class PaymentStore(context:Context){ private val prefs=context.getSharedPreferences("payments",Context.MODE_PRIVATE)
 fun saveIfNew(raw:String,p:Payment):Boolean { val id=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)}; if(prefs.contains(id))return false; prefs.edit().putString(id,"NEW|"+p.xuiUser+"|"+p.amount+"|"+p.months+"|"+p.payer).apply(); return true }
}
