package com.snaptvnow.snapcobro
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.URLEncoder
import java.net.CookieManager
import java.net.CookiePolicy

data class XuiProbeResult(val ok:Boolean,val message:String)

class XuiReadOnlyClient {
 private val cookies=CookieManager(null,CookiePolicy.ACCEPT_ALL)

 fun probe(base:String,user:String,password:String,target:String):XuiProbeResult{
  if(base.isBlank()||user.isBlank()||password.isBlank()) return XuiProbeResult(false,"Configuración XUI incompleta.")
  return try{
   // GET only: intentionally cannot modify XUI.
   val root=get(base)
   if(root.code !in 200..399) return XuiProbeResult(false,"Panel no accesible. HTTP "+root.code)
   val loginCandidates=listOf(base.trimEnd('/')+"/login",base.trimEnd('/')+"/")
   var loginOk=false
   for(url in loginCandidates){
    val body="username="+enc(user)+"&password="+enc(password)
    val r=postLoginOnly(url,body)
    if(r.code in 200..399){loginOk=true;break}
   }
   if(!loginOk) return XuiProbeResult(false,"El panel respondió, pero no pude validar el inicio de sesión.")
   val q=enc(target)
   val pages=listOf(base.trimEnd('/')+"/lines?search="+q,base.trimEnd('/')+"/lines.php?search="+q,base.trimEnd('/')+"/line?search="+q)
   for(url in pages){
    val r=get(url)
    if(r.code in 200..399 && r.text.contains(target,true)){
     val expiry=Regex("""(?i)(?:Expires?|Expiration(?: Date)?)[^0-9]{0,30}(20\d\d[-/]\d\d[-/]\d\d(?:\s+\d\d:\d\d(?::\d\d)?)?)""").find(r.text)?.groupValues?.getOrNull(1)
     return XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+(expiry?.let{"\nVencimiento visible: "+it}?: "\nCoincidencia encontrada; vencimiento no pudo extraerse automáticamente.")+"\nNo se realizó ningún cambio.")
    }
   }
   XuiProbeResult(false,"Conexión realizada, pero no encontré una coincidencia verificable para: "+target+"\nNo se realizó ningún cambio.")
  }catch(e:Exception){XuiProbeResult(false,"Error de conexión: "+(e.message?:"desconocido")+"\nNo se realizó ningún cambio.")}
 }

 private data class R(val code:Int,val text:String)
 private fun get(url:String):R{
  val c=URL(url).openConnection() as HttpURLConnection;c.requestMethod="GET";c.instanceFollowRedirects=true;c.connectTimeout=12000;c.readTimeout=12000
  addCookies(c,url);val code=c.responseCode;saveCookies(c,url);return R(code,read(c,code))
 }
 // Authentication POST is the only POST permitted here. No line/edit/save endpoint exists in this client.
 private fun postLoginOnly(url:String,body:String):R{
  val c=URL(url).openConnection() as HttpURLConnection;c.requestMethod="POST";c.instanceFollowRedirects=true;c.connectTimeout=12000;c.readTimeout=12000;c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
  addCookies(c,url);c.outputStream.use{it.write(body.toByteArray())};val code=c.responseCode;saveCookies(c,url);return R(code,read(c,code))
 }
 private fun read(c:HttpURLConnection,code:Int)=try{(if(code>=400)c.errorStream else c.inputStream)?.bufferedReader()?.use{it.readText()}?:""}catch(_:Exception){""}
 private fun addCookies(c:HttpURLConnection,url:String){cookies.get(URI(url),emptyMap()).forEach{(k,v)->if(k.equals("Cookie",true))c.setRequestProperty("Cookie",v.joinToString("; "))}}
 private fun saveCookies(c:HttpURLConnection,url:String){cookies.put(URI(url),c.headerFields)}
 private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
}
