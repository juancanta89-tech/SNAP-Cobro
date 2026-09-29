package com.snaptvnow.snapcobro
import android.content.Context
class XuiConfigStore(context:Context){
 private val p=context.getSharedPreferences("xui_local_config",Context.MODE_PRIVATE)
 fun save(url:String,user:String,password:String){p.edit().putString("url",url.trim().trimEnd('/')).putString("user",user.trim()).putString("password",password).apply()}
 fun url()=p.getString("url","")?:""
 fun user()=p.getString("user","")?:""
 fun password()=p.getString("password","")?:""
 fun configured()=url().isNotBlank()&&user().isNotBlank()&&password().isNotBlank()
}