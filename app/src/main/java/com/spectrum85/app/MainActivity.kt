package com.spectrum85.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class MainActivity : Activity() {
 private lateinit var display: SpectrumView
 private var recorder: AudioRecord? = null
 private var visualizer: Visualizer? = null
 private var worker: Thread? = null
 private val running = AtomicBoolean(false)
 private val main = Handler(Looper.getMainLooper())
 private var source = "Microphone"
 private var bands = 20
 private var theme = 0
 private var sensitivity = 1.8f
 private var decay = 0.035f
 private var peakHold = true
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
  window.decorView.windowInsetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  display = SpectrumView(); setContentView(display)
  display.setOnClickListener { showMenu() }
  if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
  else startAudio()
 }
 override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==1 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startAudio()
  else Toast.makeText(this,"Microphone permission needed for audio analysis",Toast.LENGTH_LONG).show()
 }
 private fun showMenu() {
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,12,28,12) }
  fun choice(title:String, options:Array<String>, current:Int, onPick:(Int)->Unit) {
   val text=TextView(this).apply { this.text=title; textSize=16f; setPadding(0,18,0,5) }; layout.addView(text)
   val spinner=Spinner(this); spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
   spinner.setSelection(current); layout.addView(spinner)
   spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
    override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
    override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) { onPick(position) }
   }
  }
  choice("Display",arrayOf("Green / amber / red","Blue / turquoise","Amber"),theme){ theme=it; display.invalidate() }
  choice("Frequency bands",arrayOf("10","20","31"),listOf(10,20,31).indexOf(bands)){ bands=listOf(10,20,31)[it]; display.reset() }
  choice("Audio source",arrayOf("Microphone","Device output (experimental)"),if(source=="Microphone")0 else 1){ val next=if(it==0)"Microphone" else "Device output"; if(next!=source){source=next; startAudio()} }
  choice("Sensitivity",arrayOf("Low","Normal","High"),when(sensitivity){1.0f->0;2.8f->2;else->1}){ sensitivity=listOf(1f,1.8f,2.8f)[it] }
  choice("Bar decay",arrayOf("Slow","Normal","Fast"),when(decay){0.018f->0;0.065f->2;else->1}){decay=listOf(0.018f,0.035f,0.065f)[it]}
  val toggle=CheckBox(this).apply { text="Floating peak indicators"; isChecked=peakHold; setOnCheckedChangeListener{_,v->peakHold=v} };layout.addView(toggle)
  android.app.AlertDialog.Builder(this).setTitle("SPECTRUM 85").setView(layout).setPositiveButton("Close",null).show()
 }
 private fun startAudio() {
  stopAudio()
  if(source=="Device output") {
   try {
    val v=Visualizer(0); v.captureSize=Visualizer.getCaptureSizeRange()[1]
    v.setDataCaptureListener(object:Visualizer.OnDataCaptureListener {
     override fun onWaveFormDataCapture(v:Visualizer?, waveform:ByteArray?, rate:Int) {}
     override fun onFftDataCapture(v:Visualizer?, fft:ByteArray?, rate:Int) { if(fft!=null) display.post { display.consumeFft(fft,22050) } }
    },Visualizer.getMaxCaptureRate()/2,false,true)
    v.enabled=true;visualizer=v;return
   } catch(e:Exception) {
    source="Microphone"
    Toast.makeText(this,"Output analysis unavailable; using microphone",Toast.LENGTH_LONG).show()
   }
  }
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return
  val sampleRate=44100;val size=4096
  val min=AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
  try {
   val r=AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,max(min,size*2))
   if(r.state!=AudioRecord.STATE_INITIALIZED){r.release();return}
   recorder=r;running.set(true);r.startRecording()
   worker=Thread {
    val samples=ShortArray(size)
    while(running.get()) {
     val read=r.read(samples,0,size)
     if(read>0) { val spectrum=calculateSpectrum(samples,read,sampleRate);display.post { display.consume(spectrum) } }
    }
   }.also{it.start()}
  } catch(e:Exception){Toast.makeText(this,"Audio input error: ${e.message}",Toast.LENGTH_LONG).show()}
 }
 private fun calculateSpectrum(input:ShortArray,n:Int,rate:Int):FloatArray {
  val fftSize=4096;val real=DoubleArray(fftSize);val imag=DoubleArray(fftSize)
  for(i in 0 until min(n,fftSize)) real[i]=(input[i]/32768.0)*(0.5-0.5*cos(2.0*PI*i/(fftSize-1)))
  var j=0
  for(i in 1 until fftSize){var bit=fftSize shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val tmp=real[i];real[i]=real[j];real[j]=tmp}}
  var len=2
  while(len<=fftSize){val ang=-2*PI/len;val wr=cos(ang);val wi=sin(ang);var start=0
   while(start<fftSize){var cr=1.0;var ci=0.0;for(k in 0 until len/2){val a=start+k;val b=a+len/2;val tr=cr*real[b]-ci*imag[b];val ti=cr*imag[b]+ci*real[b];real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti;val nr=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=nr};start+=len};len*=2}
  val result=FloatArray(256)
  for(i in result.indices){val freq=30.0*(20000.0/30.0).pow((i+0.5)/256.0);val bin=(freq*fftSize/rate).roundToInt().coerceIn(1,fftSize/2-1)
   var energy=0.0;for(k in max(1,bin-1)..min(fftSize/2-1,bin+1))energy+=hypot(real[k],imag[k]);result[i]=(ln(1+energy/3.0)/5.5).toFloat().coerceIn(0f,1f)}
  return result
 }
 private fun stopAudio(){running.set(false);try{recorder?.stop()}catch(_:Exception){};try{worker?.join(250)}catch(_:Exception){};worker=null;recorder?.release();recorder=null;try{visualizer?.enabled=false;visualizer?.release()}catch(_:Exception){};visualizer=null}
 override fun onPause(){super.onPause();stopAudio()}
 override fun onResume(){super.onResume();if(::display.isInitialized && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startAudio()}
 override fun onDestroy(){stopAudio();super.onDestroy()}
 inner class SpectrumView:View(this) {
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private var levels=FloatArray(31);private var peaks=FloatArray(31);private var holds=IntArray(31)
  private var latest=FloatArray(256);private var last=System.nanoTime()
  init{setBackgroundColor(Color.BLACK)}
  fun reset(){levels=FloatArray(31);peaks=FloatArray(31);holds=IntArray(31);invalidate()}
  fun consume(s:FloatArray){latest=s;invalidate()}
  fun consumeFft(fft:ByteArray,rate:Int){val out=FloatArray(256);for(i in out.indices){val freq=30.0*(20000.0/30.0).pow((i+.5)/256.0);val k=(freq*fft.size/rate).roundToInt().coerceIn(1,fft.size/2-1);val re=fft[2*k].toInt();val im=fft[2*k+1].toInt();out[i]=(ln(1.0+hypot(re.toDouble(),im.toDouble()))/6).toFloat().coerceIn(0f,1f)};consume(out)}
  override fun onDraw(canvas:Canvas){super.onDraw(canvas)
   val dt=((System.nanoTime()-last)/1e9f).coerceIn(.005f,.08f);last=System.nanoTime()
   val w=width.toFloat();val h=height.toFloat();val margin=w*.025f;val gap=w*.006f;val bw=(w-2*margin-(bands-1)*gap)/bands
   val segCount=36;val usableH=h*.89f;val segGap=usableH/segCount*.17f;val sh=usableH/segCount-segGap
   for(i in 0 until bands){val from=i*256/bands;val to=(i+1)*256/bands
    var raw=0f;for(k in from until to)raw=max(raw,latest[k]);val target=(raw*sensitivity).coerceIn(0f,1f)
    levels[i]=if(target>levels[i]) levels[i]+(target-levels[i])*.72f else max(target,levels[i]-decay*dt*60)
    if(levels[i]>=peaks[i]){peaks[i]=levels[i];holds[i]=25}else if(holds[i]>0)holds[i]-- else peaks[i]=max(levels[i],peaks[i]-.015f*dt*60)
    val active=(levels[i]*segCount).roundToInt();val x=margin+i*(bw+gap)
    for(s in 0 until segCount){val y=h*.95f-(s+1)*(sh+segGap);paint.color=if(s<active) colour(s.toFloat()/segCount) else Color.rgb(6,10,10)
     canvas.drawRoundRect(x,y,x+bw,y+sh,1.6f,1.6f,paint)}
    if(peakHold){val p=(peaks[i]*segCount).toInt().coerceIn(0,segCount-1);val y=h*.95f-(p+1)*(sh+segGap);paint.color=colour(p.toFloat()/segCount);canvas.drawRoundRect(x,y,x+bw,y+sh,1.6f,1.6f,paint)}
   }
   paint.color=Color.rgb(90,90,90);paint.textSize=28f;paint.style=Paint.Style.FILL;canvas.drawText("☰",w-49f,34f,paint)
   postInvalidateDelayed(33)
  }
  private fun colour(r:Float):Int=when(theme){1->if(r<.7f)Color.rgb(0,205,210)else Color.rgb(110,225,255);2->if(r<.8f)Color.rgb(255,145,0)else Color.rgb(255,215,65);else->if(r<.62f)Color.rgb(25,225,75)else if(r<.84f)Color.rgb(255,185,25)else Color.rgb(255,55,35)}
 }
}
