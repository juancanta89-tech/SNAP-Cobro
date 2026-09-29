package com.snaptvnow.snapcobro
import android.Manifest
import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
class MainActivity:AppCompatActivity(){
 private lateinit var store:PaymentStore; private lateinit var paymentBox:TextView; private lateinit var historyBox:TextView; private lateinit var testInput:EditText
 private var current:StoredPayment?=null
 private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){refresh()}}
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);store=PaymentStore(this)
  paymentBox=findViewById(R.id.paymentBox);historyBox=findViewById(R.id.historyBox);testInput=findViewById(R.id.testInput)
  ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.READ_CONTACTS),100)
  findViewById<Button>(R.id.testButton).setOnClickListener{val raw=testInput.text.toString().trim();val p=PaymentParser.parse(raw);if(p==null)Toast.makeText(this,"SMS no reconocido o importe sin plan",Toast.LENGTH_LONG).show() else {val ok=store.saveIfNew(raw,p);Toast.makeText(this,if(ok)"Pago de prueba detectado" else "Pago duplicado ignorado",Toast.LENGTH_LONG).show();refresh()}}
  findViewById<Button>(R.id.processButton).setOnClickListener{current?.let{store.setStatus(it.id,"TEST_OK");Toast.makeText(this,"Modo prueba: XUI NO fue modificado",Toast.LENGTH_LONG).show();refresh()}}
  findViewById<Button>(R.id.ignoreButton).setOnClickListener{current?.let{store.setStatus(it.id,"IGNORED");refresh()}}
  refresh()
 }
 override fun onStart(){super.onStart();registerReceiver(receiver,IntentFilter(PaymentStore.ACTION_PAYMENT_UPDATED),RECEIVER_NOT_EXPORTED);refresh()}
 override fun onStop(){unregisterReceiver(receiver);super.onStop()}
 private fun refresh(){val all=store.list();current=all.firstOrNull{it.status=="NEW"};paymentBox.text=current?.let{val p=it.payment;"NUEVO PAGO DETECTADO\n\nUsuario XUI: "+p.xuiUser+"\nPagador: "+p.payer+"\nImporte: $"+String.format("%.2f",p.amount)+"\nPlan: "+p.months+(if(p.months==1)" mes" else " meses")+"\nEstado: PENDIENTE DE PRUEBA"}?:"Sin pagos pendientes.\nEsperando nuevo pago de BofA…";historyBox.text=if(all.isEmpty())"Todavía no hay operaciones." else all.take(8).joinToString("\n\n"){val p=it.payment;p.xuiUser+" • $"+String.format("%.2f",p.amount)+" • "+p.months+"m\nEstado: "+it.status}}
}