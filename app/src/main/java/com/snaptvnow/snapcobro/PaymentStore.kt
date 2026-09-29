package com.snaptvnow.snapcobro
import android.content.Context
import android.content.Intent
import java.security.MessageDigest
data class StoredPayment(val id:String,val status:String,val payment:Payment)
class PaymentStore(private val context:Context){
 private val prefs=context.getSharedPreferences("payments",Context.MODE_PRIVATE)
 fun saveIfNew(raw:String,p:Payment):Boolean{
  val id=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)}
  if(prefs.contains(id))return false
  prefs.edit().putString(id,"NEW|"+p.xuiUser+"|"+p.amount+"|"+p.months+"|"+p.payer).apply()
  context.sendBroadcast(Intent(ACTION_PAYMENT_UPDATED).setPackage(context.packageName)); return true
 }
 fun list():List<StoredPayment> = prefs.all.mapNotNull { e ->
  val v=e.value as? String ?: return@mapNotNull null
  val x=v.split("|",limit=5); if(x.size<5)return@mapNotNull null
  StoredPayment(e.key,x[0],Payment(x[4],x[2].toDoubleOrNull()?:0.0,x[1],x[3].toIntOrNull()?:0))
 }
 fun setStatus(id:String,status:String){ val old=prefs.getString(id,null)?:return; val x=old.split("|",limit=5); if(x.size<5)return; prefs.edit().putString(id,status+"|"+x.drop(1).joinToString("|")).apply() }
 companion object{ const val ACTION_PAYMENT_UPDATED="com.snaptvnow.snapcobro.PAYMENT_UPDATED" }
}