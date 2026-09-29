package com.snaptvnow.snapcobro
import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
class MainActivity:AppCompatActivity(){ override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.READ_CONTACTS),100) } }
