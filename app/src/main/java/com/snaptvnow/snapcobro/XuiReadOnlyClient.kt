package com.snaptvnow.snapcobro
import java.net.*
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.*
import java.time.format.DateTimeFormatter

data class XuiProbeResult(val ok:Boolean,val message:String)

class XuiReadOnlyClient {
 private val cookies=CookieManager(null,CookiePolicy.ACCEPT_ALL)

 fun probe(base:String,user:String,password:String,target:String,months:Int):XuiProbeResult{
  if(base.isBlank()||user.isBlank()||password.isBlank()) return XuiProbeResult(false,"Configuración XUI incompleta.")
  return try{
   val root=get(base)
   if(root.code !in 200..399) return XuiProbeResult(false,"Panel no accesible. HTTP "+root.code)
   var loginOk=false
   for(url in listOf(base.trimEnd('/')+"/login",base.trimEnd('/')+"/")){
    val r=postLoginOnly(url,"username="+enc(user)+"&password="+enc(password))
    if(r.code in 200..399){loginOk=true;break}
   }
   if(!loginOk) return XuiProbeResult(false,"No pude validar el inicio de sesión en XUI. El acceso raíz respondió HTTP "+root.code+". No se realizó ningún cambio.")
   val q=enc(target)
   val pages=listOf(base.trimEnd('/')+"/lines?search="+q,base.trimEnd('/')+"/lines.php?search="+q,base.trimEnd('/')+"/line?search="+q)
   for(url in pages){
    val r=get(url)
    if(r.code !in 200..399 || !r.text.contains(target,true)) continue
    val expiry=findExpiryNearUser(r.text,target)
    if(expiry!=null){
     val proposed=addMonths(expiry,months)
     return XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+"\nVencimiento actual: "+expiry+"\nExtensión detectada: +"+months+(if(months==1)" mes" else " meses")+"\nNueva fecha propuesta: "+proposed+"\n\nVERIFICACIÓN PENDIENTE • No se realizó ningún cambio.")
    }
    // Some XUI list views expose the line but not its expiry. Follow read-only links around the exact row.
    val links=(extractLinksNearUser(r.text,target)+extractCandidateLineUrls(r.text,target,base)).distinct()
    for(link in links.take(40)){
     val detail=get(resolve(url,link))
     if(detail.code in 200..399){
      val d=findExpiryNearUser(detail.text,target)?:findAnyExpiry(detail.text)
      if(d!=null){
       return XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+"\nVencimiento actual: "+d+"\nExtensión detectada: +"+months+(if(months==1)" mes" else " meses")+"\nNueva fecha propuesta: "+addMonths(d,months)+"\n\nVERIFICACIÓN PENDIENTE • No se realizó ningún cambio.")
      }
     }
    }
    val ajax=probeReadOnlyDataRoutes(base,target,months)
    return XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+"\nDIAGNÓSTICO HTML: enlaces/IDs candidatos: "+links.size+"\nDIAGNÓSTICO DATOS: "+ajax+"\nLa línea fue localizada, pero todavía no pude leer el vencimiento.\nNo se realizó ningún cambio.")
   }
   XuiProbeResult(false,"Conexión realizada, pero no encontré una coincidencia verificable para: "+target+"\nNo se realizó ningún cambio.")
  }catch(e:Exception){XuiProbeResult(false,"Error de conexión: "+(e.message?:"desconocido")+"\nNo se realizó ningún cambio.")}
 }

 private fun findExpiryNearUser(html:String,user:String):String?{
  val i=html.indexOf(user,ignoreCase=true); if(i<0)return null
  val a=(i-5000).coerceAtLeast(0); val b=(i+10000).coerceAtMost(html.length)
  return findAnyExpiry(html.substring(a,b))
 }
 private fun findAnyExpiry(s:String):String?{
  val decoded=s.replace("&quot;","\"").replace("&#039;","'").replace("&nbsp;"," ")
  val patterns=listOf(
   Regex("""(?is)(?:expiration(?:[_\s-]*date)?|expires?|exp[_\s-]*date)[\s\S]{0,800}?(20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)"""),
   Regex("""(?is)(?:name|id)\s*=\s*["'][^"']*(?:exp|expiration)[^"']*["'][^>]*value\s*=\s*["'](20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)["']"""),
   Regex("""(?is)(?:expiration(?:[_\s-]*date)?|expires?|exp[_\s-]*date)[^0-9]{0,180}(\d{1,2}[-/]\d{1,2}[-/]20\d{2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)""")
  )
  for(p in patterns){p.find(decoded)?.groupValues?.getOrNull(1)?.let{return normalize(it)}}
  return null
 }
 private fun normalize(v:String)=v.trim().replace('/','-')
 private fun addMonths(v:String,months:Int):String{
  val parts=v.split(Regex("""\s+"""),limit=2); val d=parts[0].split("-")
  return try{
   val date=if(d[0].length==4) LocalDate.of(d[0].toInt(),d[1].toInt(),d[2].toInt()) else LocalDate.of(d[2].toInt(),d[0].toInt(),d[1].toInt())
   date.plusMonths(months.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)+(if(parts.size>1)" "+parts[1] else "")
  }catch(_:Exception){"No calculable"}
 }
 private fun extractLinksNearUser(html:String,user:String):List<String>{
  val i=html.indexOf(user,ignoreCase=true);if(i<0)return emptyList()
  val chunk=html.substring((i-5000).coerceAtLeast(0),(i+8000).coerceAtMost(html.length))
  val urls=mutableListOf<String>()
  Regex("""(?is)href\s*=\s*["']([^"'#]+)["']""").findAll(chunk).forEach{urls+=it.groupValues[1]}
  Regex("""(?is)(?:data-url|data-href|url)\s*=\s*["']([^"']+)["']""").findAll(chunk).forEach{urls+=it.groupValues[1]}
  Regex("""(?is)(?:window\.location|location\.href)\s*=\s*["']([^"']+)["']""").findAll(chunk).forEach{urls+=it.groupValues[1]}
  Regex("""(?is)(?:line\?id=|data-id\s*=\s*["'])(\d{2,})""").findAll(chunk).forEach{urls+="/line?id="+it.groupValues[1]}
  return urls.map{it.replace("&amp;","&")}.filter{it.contains("line",true)||it.contains("edit",true)}.distinct()
 }
 private fun extractCandidateLineUrls(html:String,user:String,base:String):List<String>{
  val decoded=html.replace("&quot;","\"").replace("&#039;","'").replace("&amp;","&")
  val i=decoded.indexOf(user,ignoreCase=true); if(i<0)return emptyList()
  val chunks=listOf(
   decoded.substring((i-30000).coerceAtLeast(0),(i+30000).coerceAtMost(decoded.length)),
   decoded
  )
  val out=mutableListOf<String>()
  val patterns=listOf(
   Regex("""(?is)(?:line(?:\.php)?\?id=)(\d{2,})"""),
   Regex("""(?is)(?:data-id|data-line-id|line_id|stream_id|id)\s*[=:]\s*["']?(\d{2,})"""),
   Regex("""(?is)["'](?:id|line_id|stream_id)["']\s*:\s*["']?(\d{2,})""")
  )
  for(chunk in chunks) for(p in patterns) p.findAll(chunk).forEach{m->out+=base.trimEnd('/')+"/line?id="+m.groupValues[1]}
  return out.distinct().take(40)
 }
 private fun probeReadOnlyDataRoutes(base:String,target:String,months:Int):String{
  val q=enc(target)
  val routes=listOf(
   "/lines?draw=1&start=0&length=25&search[value]="+q,
   "/lines.php?draw=1&start=0&length=25&search[value]="+q
  )
  val notes=mutableListOf<String>()
  for(path in routes){
   try{
    val r=get(base.trimEnd('/')+path)
    if(r.code !in 200..399){notes+=path.substringBefore('?')+":"+r.code;continue}
    if(!r.text.contains(target,true)){notes+=path.substringBefore('?')+":"+r.code;continue}
    val ids=extractIdsFromDataResponse(r.text,target)
    notes+=path.substringBefore('?')+":"+r.code+":USER:IDs="+ids.take(5).joinToString(",")
    for(id in ids.take(20)){
     val detail=get(base.trimEnd('/')+"/line?id="+id)
     if(detail.code in 200..399){
      val expiry=findAnyExpiry(detail.text)
      if(expiry!=null) return "FOUND id="+id+" | Vencimiento actual: "+expiry+" | Nueva fecha propuesta: "+addMonths(expiry,months)
     }
    }
   }catch(_:Exception){notes+=path.substringBefore('?')+":ERR"}
  }
  return notes.joinToString(" | ")
 }
 private fun extractIdsFromDataResponse(body:String,user:String):List<String>{
  val decoded=body.replace("\\/","/").replace("&quot;","\"").replace("&#039;","'").replace("&amp;","&")
  val i=decoded.indexOf(user,ignoreCase=true);if(i<0)return emptyList()
  val chunk=decoded.substring((i-5000).coerceAtLeast(0),(i+5000).coerceAtMost(decoded.length))
  val out=mutableListOf<String>()
  listOf(
   Regex("""(?is)line(?:\.php)?\?id[=\\u003d]+(\d{2,})"""),
   Regex("""(?is)["'](?:id|line_id|stream_id)["']\s*:\s*["']?(\d{2,})"""),
   Regex("""(?is)(?:data-id|data-line-id)\s*=\s*["'](\d{2,})["']""")
  ).forEach{p->p.findAll(chunk).forEach{m->out+=m.groupValues[1]}}
  return out.distinct()
 }
 private fun resolve(base:String,link:String)=URL(URL(base),link).toString()
 private data class R(val code:Int,val text:String)
 private fun get(url:String):R{val c=URL(url).openConnection() as HttpURLConnection;c.requestMethod="GET";c.instanceFollowRedirects=true;c.connectTimeout=12000;c.readTimeout=12000;addCookies(c,url);val code=c.responseCode;saveCookies(c,url);return R(code,read(c,code))}
 // The only POST is authentication. No update/save/delete endpoint is implemented.
 private fun postLoginOnly(url:String,body:String):R{val c=URL(url).openConnection() as HttpURLConnection;c.requestMethod="POST";c.instanceFollowRedirects=true;c.connectTimeout=12000;c.readTimeout=12000;c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");addCookies(c,url);c.outputStream.use{it.write(body.toByteArray())};val code=c.responseCode;saveCookies(c,url);return R(code,read(c,code))}
 private fun read(c:HttpURLConnection,code:Int)=try{(if(code>=400)c.errorStream else c.inputStream)?.bufferedReader()?.use{it.readText()}?:""}catch(_:Exception){""}
 private fun addCookies(c:HttpURLConnection,url:String){cookies.get(URI(url),emptyMap()).forEach{(k,v)->if(k.equals("Cookie",true))c.setRequestProperty("Cookie",v.joinToString("; "))}}
 private fun saveCookies(c:HttpURLConnection,url:String){cookies.put(URI(url),c.headerFields)}
 private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
}