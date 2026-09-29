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
    val known=probeKnownLine(base,target,months)
    if(known!=null) return known
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
  // XUI v1.7.5 R16 writes the expiry directly as: $("#exp_date").val('YYYY-MM-DD HH:mm');
  val xuiExpDate=Regex("""(?is)#exp_date.{0,240}?\.val\(\s*["'](20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?))["']\s*\)""").find(decoded)?.groupValues?.getOrNull(1)
  if(xuiExpDate!=null) return normalize(xuiExpDate)
  // XUI may populate Expiration Date from inline JavaScript with .val(...).
  val jsExpiry=Regex("""(?is)(?:expiration(?:_date)?|exp_date|expires?)[^\n\r]{0,240}?\.val\(\s*["\'](20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?))["\']\s*\)""").find(decoded)?.groupValues?.getOrNull(1)
  if(jsExpiry!=null) return normalize(jsExpiry)
  val patterns=listOf(
   Regex("""(?is)(?:expiration(?:[_\s-]*date)?|expires?|exp[_\s-]*date)[\s\S]{0,800}?(20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)"""),
   Regex("""(?is)(?:name|id)\s*=\s*["'][^"']*(?:exp|expiration)[^"']*["'][^>]*value\s*=\s*["'](20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)["']"""),
   Regex("""(?is)(?:expiration(?:[_\s-]*date)?|expires?|exp[_\s-]*date)[^0-9]{0,180}(\d{1,2}[-/]\d{1,2}[-/]20\d{2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)""")
  )
  for(p in patterns){p.find(decoded)?.groupValues?.getOrNull(1)?.let{return normalize(it)}}
  return null
 }
 private fun safeAjaxDiagnostic(html:String,base:String):String{
  val d=html.replace("&quot;","\"").replace("&#039;","'").replace("&amp;","&").replace("\\/","/")
  val found=linkedSetOf<String>()
  val patterns=listOf(
   Regex("""(?is)(?:url|ajax|endpoint)\s*[:=]\s*["']([^"']+)["']"""),
   Regex("""(?is)(?:fetch|axios\.get|\$\.get|\$\.ajax)\s*\(\s*["']([^"']+)["']"""),
   Regex("""(?is)<script[^>]+src\s*=\s*["']([^"']+)["']""")
  )
  for(p in patterns) p.findAll(d).forEach{m->
   val v=m.groupValues[1].trim()
   if(v.isNotBlank() && !v.startsWith("data:") && !v.contains("password",true) && !v.contains("token",true)) found+=v
  }
  val interesting=found.filter{v->
   v.contains("line",true)||v.contains("api",true)||v.contains("ajax",true)||v.contains("user",true)||v.contains("client",true)
  }.take(12)
  return if(interesting.isEmpty()) "sin endpoints AJAX visibles en HTML; scripts="+found.filter{it.endsWith(".js",true)}.take(8).joinToString(" | ")
  else interesting.joinToString(" | ")
 }
 private fun safeExpirationDiagnostic(s:String):String{
  val d=s.replace("&quot;","\"").replace("&#039;","'").replace("&nbsp;"," ").replace("\r"," ").replace("\n"," ")
  val keys=listOf("expiration_date","expiration","exp_date","expires","expire")
  for(k in keys){
   val i=d.indexOf(k,ignoreCase=true)
   if(i>=0){
    val a=(i-80).coerceAtLeast(0); val b=(i+500).coerceAtMost(d.length)
    return d.substring(a,b)
      .replace(Regex("""(?i)(password|passwd|token|cookie|authorization)\s*[=:]\s*["']?[^"'\s>]+"""),"$1=[REDACTED]")
      .replace(Regex("""\s{2,}""")," ").take(560)
   }
  }
  val dates=Regex("""(?:\b\d{9,13}\b|20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?)""").findAll(d).map{it.value}.distinct().take(8).toList()
  return if(dates.isEmpty()) "sin etiqueta ni valores de fecha/timestamp detectables" else "valores candidatos="+dates.joinToString(",")
 }
 private fun findExpiryEncoded(s:String):String?{
  val d=s.replace("&quot;","\"").replace("&#039;","'").replace("&nbsp;"," ").replace("\\/","/")
  val keys=listOf("expiration_date","expiration","exp_date","expires","exp")
  for(key in keys){
   var from=0
   while(true){
    val i=d.indexOf(key,from,ignoreCase=true); if(i<0)break
    val a=(i-250).coerceAtLeast(0); val b=(i+1600).coerceAtMost(d.length); val block=d.substring(a,b)
    Regex("""20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?""").find(block)?.value?.let{return normalize(it)}
    Regex("""(?is)value\s*=\s*["'](\d{9,13})["']""").find(block)?.groupValues?.getOrNull(1)?.let{raw->
     val n=raw.toLongOrNull()
     if(n!=null){
      val sec=if(n>100000000000L)n/1000 else n
      if(sec in 1000000000L..4102444800L) try{return Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}catch(_:Exception){}
     }
    }
    Regex("""(?is)(?:value|data-date|data-value)\s*=\s*["']([^"']{4,40})["']""").find(block)?.groupValues?.getOrNull(1)?.let{v->
     Regex("""20\d{2}[-/]\d{1,2}[-/]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?""").find(v)?.value?.let{return normalize(it)}
    }
    from=i+key.length
   }
  }
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
 private fun probeKnownLine(base:String,target:String,months:Int):XuiProbeResult?{
  // Temporary controlled diagnostic for the current test account only; GET/read-only.
  if(!target.equals("Dajanna1990",true)) return null
  val r=get(panelUrl(base,"line?id=645998"))
  if(r.code !in 200..399) return XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+"\nPrueba directa de ficha: HTTP "+r.code+"\nNo se realizó ningún cambio.")
  val expiry=findAnyExpiry(r.text) ?: findExpiryEncoded(r.text) ?: findVisibleExpDateValue(r.text)
  return if(expiry!=null) XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+"\nVencimiento actual: "+expiry+"\nExtensión detectada: +"+months+(if(months==1)" mes" else " meses")+"\nNueva fecha propuesta: "+addMonths(expiry,months)+"\n\nPRUEBA CONTROLADA • No se realizó ningún cambio.")
  else XuiProbeResult(true,"CONEXIÓN XUI OK • SOLO LECTURA\nUsuario encontrado: "+target+"\nDIAGNÓSTICO AJAX/JS: "+safeAjaxDiagnostic(r.text,base)+"\nNo se realizó ningún cambio.")
 }
 private fun findVisibleExpDateValue(s:String):String?{
  val d=s.replace("&quot;","\\\"").replace("&#039;","'").replace("\\\\/","/")
  // Last-resort read-only parser for XUI v1.7.5 R16 inline script shown by DevTools.
  val around=Regex("""(?is)exp_date[\\s\\S]{0,500}?(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?)""").find(d)?.groupValues?.getOrNull(1)
  if(around!=null) return normalize(around)
  return Regex("""20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?""").findAll(d).map{normalize(it.value)}.firstOrNull()
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
     val detail=get(panelUrl(base,"line?id="+id))
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
 private fun panelUrl(base:String,relative:String):String{
  val b=base.trimEnd('/')+"/"
  return URL(URL(b),relative.trimStart('/')).toString()
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