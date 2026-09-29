package com.snaptvnow.snapcobro
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
class SmsReceiver:BroadcastReceiver(){ override fun onReceive(context:Context,intent:Intent){ if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return; val body=Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString(""){it.messageBody?:""}; val payment=PaymentParser.parse(body)?:return; PaymentStore(context).saveIfNew(body,payment) } }
