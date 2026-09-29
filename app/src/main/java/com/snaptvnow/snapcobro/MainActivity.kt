package com.snaptvnow.snapcobro
import android.Manifest
import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity:AppCompatActivity(){
 private lateinit var store:PaymentStore
 private lateinit var paymentBox:TextView
 private lateinit var historyBox:TextView
 private lateinit var renewalPreview:TextView
 private lateinit var testInput:EditText
 private var current:StoredPayment?=null
 private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){refresh()}}

 override fun onCreate(b:Bundle?){
  super.onCreate(b);setContentView(R.layout.activity_main);store=PaymentStore(this)
  paymentBox=findViewById(R.id.paymentBox);historyBox=findViewById(R.id.historyBox)
  renewalPreview=findViewById(R.id.renewalPreview);testInput=findViewById(R.id.testInput)
  ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.READ_CONTACTS),100)

  findViewById<Button>(R.id.testButton).setOnClickListener{
   val raw=testInput.text.toString().trim();val p=PaymentParser.parse(raw)
   if(p==null) Toast.makeText(this,"SMS no reconocido o importe sin plan",Toast.LENGTH_LONG).show()
   else {val ok=store.saveIfNew(raw,p);Toast.makeText(this,if(ok)"Pago de prueba detectado" else "Pago duplicado ignorado",Toast.LENGTH_LONG).show();refresh()}
  }
  refresh()
 }

 override fun onStart(){super.onStart();registerReceiver(receiver,IntentFilter(PaymentStore.ACTION_PAYMENT_UPDATED),RECEIVER_NOT_EXPORTED);refresh()}
 override fun onStop(){unregisterReceiver(receiver);super.onStop()}

 private fun refresh(){
  val all=store.list();current=all.firstOrNull{it.status=="NEW"}
  paymentBox.text=current?.let{val p=it.payment;"NUEVO PAGO DETECTADO\n\nUsuario XUI: "+p.xuiUser+"\nPagador: "+p.payer+"\nImporte: $"+String.format("%.2f",p.amount)+"\nPlan: "+p.months+(if(p.months==1)" mes" else " meses")+"\nEstado: PENDIENTE DE VERIFICACIÓN"}?:"Sin pagos pendientes.\nEsperando nuevo pago de BofA…"
  renewalPreview.text=current?.let{val p=it.payment;"Usuario a buscar en XUI: "+p.xuiUser+"\nPlan pagado: "+p.months+(if(p.months==1)" mes" else " meses")+"\n\nSIGUIENTE ETAPA SEGURA:\n1. Buscar coincidencia exacta en XUI\n2. Leer vencimiento actual\n3. Calcular nueva fecha\n4. Mostrar confirmación\n5. Guardar y verificar respuesta\n\nXUI BLOQUEADO: todavía no se modifica ninguna línea."}?:"Selecciona un pago pendiente para preparar la renovación.\n\nXUI permanece bloqueado: esta pantalla NO guarda cambios."
  historyBox.text=if(all.isEmpty())"Todavía no hay operaciones." else all.take(8).joinToString("\n\n"){val p=it.payment;p.xuiUser+" • $"+String.format("%.2f",p.amount)+" • "+p.months+"m\nEstado: "+it.status}
 }
}