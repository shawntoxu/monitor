package api.proxy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * 
 * @author shawn.wang
 * 
 *   get container and usage info 
 * 
 */
public class Util {
	
	private static final Log  log  = LogFactory.getLog(Util.class) ;
	
	private static final String GETNAMESPACE = "http://%s/api/v1/namespaces";
	private static final String GETHOST = "http://%s/api/v1/nodes";
	private static final String GETPODS = "http://%s/api/v1/namespaces/%s/pods";
	// paas-agent
	@Deprecated
	private static final String DOCKER_API = "http://%s:12305/api/v1.0/docker"; 
	//cadvisor api
	public static final String CADVISOR_API = "http://%s:4194/api/v1.2/docker" ; 
	
	private static Set<String> nodelist = new CopyOnWriteArraySet<String>();
	private static Set<String> nslist = new CopyOnWriteArraySet<String>() ; 
	
	// pod usage info : podname -> cpu@mem 
	private static Map<String, String> m = new ConcurrentHashMap<String,String>();
	
	// app info :  namespace -> appjson
	private static Map<String, String> appinfo = new ConcurrentHashMap<String,String>();
	
	//k8s api server or proxy address  
	public static String server = null;
	
	//how many thread will start to get pod info / consider the node number   
	public static int thread_base = 3 ; 

//   get single instace obj  

//	private static final Util u = new Util();

//	private void Util() {
//	}
//
//	public static Util getUtil() {
//		return u;
//	}

	private static JSONObject getResult(String address)  {

		JSONObject jsonObj = null;
		try {
			HttpClient client = new HttpClient();
			client.setTimeout(3000);
			GetMethod getMethod = new GetMethod(address);
			client.executeMethod(getMethod);
			// String response = getMethod.getResponseBodyAsString();
			String response = IOUtils.toString(getMethod.getResponseBodyAsStream(), "UTF-8");
			jsonObj = new JSONObject(response);
		} catch (Exception e) {
			log.error("call " + address + " failed : " + e.toString());
		}
		return jsonObj == null ? null : jsonObj;
	}
	
	private static Set<String> getItemNames(String address){
		Set<String> l = new HashSet<String>();
		JSONObject obj = getResult(address);
		try {
			JSONArray jsonArray = obj.getJSONArray("items");
			for (Object o : jsonArray) {
				JSONObject jobj = (JSONObject) o;
				l.add(jobj.getJSONObject("metadata").get("name").toString());
			}
		} catch (Exception e) {
			log.error("getItems  " + address + " failed : " + e.toString());
		}
		
		return  l ; 
	}

	private static void getNode() {
		Set<String> nl = getItemNames(String.format(GETHOST, server)); 
		if(nl.size() > 0 ){
			for (String n : nl) {
				nodelist.add(n);
			}
		}
	}

	private  static void setAppInfo(String appname) {
		JSONObject jsonObj = getResult(String.format(GETPODS, server, appname));
		JSONArray jsonArray =null ; 
		try {
		if(jsonObj.getJSONArray("items") != null ){
			jsonArray = jsonObj.getJSONArray("items");
			for (Object obj : jsonArray) {
				JSONObject jobj = (JSONObject) obj;
				String podname = jobj.getJSONObject("metadata").get("name").toString();
				String age = Util.getT(jobj.getJSONObject("status").get("startTime").toString());
				if (m.containsKey(podname)) {
					String usage = m.get(podname);
					jobj.getJSONObject("metadata").put("cpu", usage.split("@")[0]);
					jobj.getJSONObject("metadata").put("mem", usage.split("@")[1]);
				} else {
					// set a default value if not found the usage
					jobj.getJSONObject("metadata").put("cpu", "1");
					jobj.getJSONObject("metadata").put("mem", "1");
				}
				// set age to result json
				JSONObject newShop = new JSONObject();
				jobj.getJSONObject("metadata").put("age", age);
			}
		}
			if(null != jsonObj ){
				appinfo.put(appname, jsonObj.toString()) ;
			}
			
		} catch (Exception e) {
			log.error("get app " + appname + " Exception " + e.getMessage());
		}

	}
   
	
	public static String getAppInfo(String appname) {
			return appinfo.get(appname)  ;
	}
	
	private static void getNameSpace(){
		
		Set<String> nl = getItemNames(String.format(GETNAMESPACE, server)); 
		for (String ns : nl) {
			if(!ns.equalsIgnoreCase("default")){ 
			 nslist.add(ns) ;
			}
		}
	}
	
	
	
	private static void getUsage() {
		// List<String> t = new ArrayList<String>() ;
		// t.add("10.2.33.34");
	
		for (String n : nodelist) {
			// for (String n : t) {
			try {
				// System.out.println(" node------------------ " + n ) ;
				JSONObject info = getResult(String.format(DOCKER_API, n));
				Iterator iterator = info.keys();
				while (iterator.hasNext()) {
					String key = (String) iterator.next();
					JSONObject value = info.getJSONObject(key);
					// drop k8s_POD container info
					if (null != value.getJSONObject("labels").get("io.kubernetes.container.name") && value
							.getJSONObject("labels").get("io.kubernetes.container.name").toString().equals("POD")) {
						continue;
					}
					String cpu = value.getJSONObject("stats").getJSONObject("cpu").getBigDecimal("cpu_percentage")
							.toString();
					if ("0.0".equals(cpu)) {
						cpu = "0.01";
					}
					long mem = Long.parseLong(
							value.getJSONObject("stats").getJSONObject("memory").get("usage").toString()) / 1024 / 1024;
					// System.out.println(value.get("pod_name").toString()+" " +
					// value.getJSONObject("stats").getJSONObject("memory").get("usage").toString());
					if (null != value.get("pod_name") && value.get("pod_name").toString().trim().length() > 1
							&& value.get("pod_name").toString().indexOf("paas-agent") < 0) {
						m.put(value.get("pod_name").toString(), cpu + "@" + mem);
					}
				}
				// System.out.println(info.get(iterator.next().toString()));
			} catch (Exception e) {
				System.out.println(" get node info " + n + " EXCEPTION £º" + e.toString());
				continue;
			}
			
		}
	}

	
	/**
	 * get container  running times 
	 * @param date
	 * @return
	 */
	public static String getT(String date) {
		long t = 0l;
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		Date d;
		try {
			d = f.parse(date);
			long milliseconds = d.getTime();
			t = (System.currentTimeMillis() - milliseconds) / 1000;
		} catch (ParseException e) {
			e.printStackTrace();
		}

		String tt = "";
		long days = t / (3600 * 24);
		long hours = t / 3600;
		long mins = t / 60;
		long seconds = t;
		if (days > 0) {
			tt = String.format("%sd", days);
		} else if (hours > 0)
			tt = String.format("%sh", hours);
		else if (mins > 0) {
			tt = String.format("%sm", mins);
		} else if (seconds > 0) {
			tt = String.format("%ss", seconds);
		}

		return tt;

	}
	
	
	private static void getUsageNew(ExecutorService service){
		int nn  = Util.nodelist.size()  ;
		int ahalf  = nn / thread_base  ; 
		
		System.out.println("will start " + ahalf + " thread  to compute ")  ;
	
		for(int i=0; i<ahalf ; i++){
			final Set<String> set1  ;
			if(i+1 == ahalf){
				 set1  = Util.nodelist
						.stream()
						.skip(i*thread_base)
						.limit(nodelist.size())
						.collect(Collectors.toSet());
			}else{
			    set1  = Util.nodelist
					.stream()
					.skip(i*thread_base)
					.limit(thread_base)
					.collect(Collectors.toSet());
			}
			// start a stack  single thread 
//			Thread b = new Thread(new Runnable() {
//				@Override
//				public void run() {
//					System.out.println("start thread " + Thread.currentThread().getName())  ;
//					getUsageNew(set1);
//					System.out.println("thread " + Thread.currentThread().getName() + " end ")  ;
//				}
//			});
//			b.start(); 
			//start a task with a thread in the thredpool 
			service.execute(new Runnable() {
				@Override
				public void run() {
//					System.out.println("start thread " + Thread.currentThread().getName())  ;
					getUsageNew(set1);
//					System.out.println("thread " + Thread.currentThread().getName() + " end ")  ;
				}
			});
			
		
			
		}
		
//		System.out.println(" end get info " + f.format(new Date(t)).toString());
		
	}

	static class Node implements Runnable {
		@Override
		public void run() {
			while (true) {
				getNode();
				try {
					log.info(Thread.currentThread().getName());
					Thread.currentThread().sleep(60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	static class Usage implements Runnable {
		private ExecutorService service  ; 
		
		public Usage(ExecutorService service){
			this.service = service ; 
			
		}
		@Override
		public void run() {
			getUsageNew(service);
			
		}
	}
	
	
	static class NsInfo implements Runnable {
		@Override
		public void run() {
			while (true) {
				getNameSpace() ;
				try {
					log.info(Thread.currentThread().getName());
					Thread.currentThread().sleep( 10 * 60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	
	static class AppInfo implements Runnable {
		@Override
		public void run() {
			while (true) {
				try {
					for (String n : nslist) {
						setAppInfo(n) ; 
					}
					log.info(Thread.currentThread().getName());
					Thread.currentThread().sleep(10 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	


	
	static class OldInfoGc implements Runnable {
		@Override
		public void run() {
			//GC app 
			Set<String> nl = getItemNames(String.format(GETNAMESPACE, server));
			nslist.retainAll(nl) ;
			
			//GC appinfo 
			appinfo.keySet().retainAll(nl) ;
			
			//GC host 
			Set<String> hl = getItemNames(String.format(GETHOST, server));
			nodelist.retainAll(hl) ;
			
			//GC pod
			Set<String>  keys = new HashSet<String>() ; 
//			for (String n : nodelist) {
//				try {
//					JSONObject info = getResult(String.format(DOCKER_API, n));
//					Iterator iterator = info.keys();
//					while (iterator.hasNext()) {
//						String key = (String) iterator.next();
//						JSONObject value = info.getJSONObject(key);
//						if (null != value.getJSONObject("labels").get("io.kubernetes.container.name") && value
//								.getJSONObject("labels").get("io.kubernetes.container.name").toString().equals("POD")) {
//							continue;
//						}
//						if (null != value.get("pod_name") && value.get("pod_name").toString().trim().length() > 1
//								&& value.get("pod_name").toString().indexOf("paas-agent") < 0) {
//							keys.add(value.get("pod_name").toString()) ;
//						}
//					}
//				} catch (Exception e) {
//					System.out.println(" GC get node info " + n + " EXCEPTION £º" + e.toString());
//					continue;
//				}
//				
//			}
			
			for (String node : nodelist) {
				JSONObject  jobj = getResult(String.format(CADVISOR_API, node)) ;
				if(jobj != null ) {
				Iterator iterator = jobj.keys();
				while (iterator.hasNext()) {
					String key = (String) iterator.next();
					
					JSONObject value = jobj.getJSONObject(key);
					if (null != value.getJSONObject("labels").get("io.kubernetes.container.name") && ( 
							value.getJSONObject("labels").get("io.kubernetes.container.name").toString().equals("POD") || 
							value.getJSONObject("labels").get("io.kubernetes.pod.name").toString().indexOf("paas-agent") != -1)) {
						continue;
					}else{
						if (null != value.getJSONObject("labels").get("io.kubernetes.pod.name") 
								&& value.getJSONObject("labels").get("io.kubernetes.pod.name").toString().trim().length() > 1) {
							keys.add(value.get("pod_name").toString()) ;
						}
					}
				}
				
			 } 
				
			}
			
			m.keySet().retainAll(keys) ; 
			
		}
	}
	
	

	public static void getinfo() {
		try {
    	ExecutorService service = Executors.newCachedThreadPool() ;
    	ScheduledExecutorService service2 = Executors.newScheduledThreadPool(2);
	
		getNameSpace() ;
		getNode();
//		getUsage();
		Thread.currentThread().sleep(2000);
		service2.scheduleAtFixedRate(new Usage(service),1,10,TimeUnit.SECONDS) ;
		
		log.info("usage thread  init is completed");
		
//		getUsageNew(service);
		
		Thread.currentThread().sleep(5000);
		new Thread(new AppInfo(), "appinfThread").start();
		
		log.info("get app info thread  init is completed");

		new Thread(new NsInfo(), "nsThread").start();;
		
		log.info("get namespace info thread   init is completed");

		new Thread(new Node(), "nodeThread").start();
		
		log.info("get node info thread  init is completed");

//		new Thread(new Usage(), "UsageThread").start(); 
		
		//start GC thrad 
		service2.scheduleAtFixedRate(new OldInfoGc(),12*3600,12*3600,TimeUnit.SECONDS) ;
		
		log.info("do gc  thread  init is completed");
		
		
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	
	public static long TimetoLong(String date) {
		long t = 0l;
		date = date.split("\\.")[0] ;
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Date d;
		long milliseconds = 0 ; 
		try {
		   d = f.parse(date);
		   milliseconds = d.getTime();
//			t = (System.currentTimeMillis() - milliseconds) / 1000;
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return milliseconds  ; 
	}
	
	/**
	 *  
	 * @param Cpu1
	 * @param Cpu2
	 * @param time
	 * @param cpu_core_num
	 * @return cpu usage 
	 * 
	 * 	CPU Usage % = (Used CPU Time (in nanoseconds) for the interval) /(interval (in nano secs) * num cores)
	 *  Here, we calculate the usage in milliseconds
	 *  
	 */
	public static String getCpuUsgae(Long Cpu1,Long Cpu2,Long time,int cpu_core_num){
		
//		System.out.println( " total usage ==== " + (Long.parseLong(Cpu1)  - Long.parseLong(Cpu2))  )  ; 
//		System.out.println((Cpu1  - Cpu2 )/(Double.parseDouble(time+""))/1000/2);
		
		Double f = (Cpu1  - Cpu2 )/(Double.parseDouble(time.toString()))/1000/cpu_core_num   ; 
		
		String r = String.format("%.2f", f)  ; 
		
		return String.format("%.2f", f)  ; 
	}
	
     
	public static String getMemUsage(Long mem){
		
		Long memu   =  mem / 1024 / 1024  ;
		
		if(0 == memu){System.out.println(mem);}
		return String.valueOf(memu)  ; 
		
	}
	
	
	public static void getUsageNew( Set<String> nlist){
		
		for (String node : nlist) {

			JSONObject  jobj = getResult(String.format(CADVISOR_API, node)) ;
			
			if(jobj != null ) {
			
			Iterator iterator = jobj.keys();
			while (iterator.hasNext()) {
				
				String key = (String) iterator.next();
				
				// just get user namespace info 
//				if(key.toString().indexOf("user") < 0 ){
//					continue  ;
//				}
				
				JSONObject value = jobj.getJSONObject(key);
				if (null != value.getJSONObject("labels").get("io.kubernetes.container.name") && ( 
						value.getJSONObject("labels").get("io.kubernetes.container.name").toString().equals("POD") || 
						value.getJSONObject("labels").get("io.kubernetes.pod.name").toString().indexOf("paas-agent") != -1)) {
					continue;
				}else{
					if (null != value.getJSONObject("labels").get("io.kubernetes.pod.name") && value.getJSONObject("labels").get("io.kubernetes.pod.name").toString().trim().length() > 1) {
						
						JSONArray jsonArray = value.getJSONArray("stats");
						if(jsonArray.length() >=2){
							JSONObject obj1 = jsonArray.getJSONObject(jsonArray.length() -1 ) ;
							
							if(obj1.getJSONObject("cpu").getJSONObject("usage").getLong("total") == 0) continue ; 
							
							JSONObject obj2 = jsonArray.getJSONObject(jsonArray.length() -2 ) ;
							
							Long time = (TimetoLong(obj1.get("timestamp").toString()) - TimetoLong(obj2.get("timestamp").toString())) ; 
//							System.out.println( node + " cpu size === " + obj1.getJSONObject("cpu").getJSONObject("usage").getJSONArray("per_cpu_usage").length());
							String cpu = getCpuUsgae(obj1.getJSONObject("cpu").getJSONObject("usage").getLong("total"),
									obj2.getJSONObject("cpu").getJSONObject("usage").getLong(("total")),
									time,
							        obj1.getJSONObject("cpu").getJSONObject("usage").getJSONArray("per_cpu_usage").length()) ;
									
							// cpu usage == 0 ,check app ?
							if(cpu.equals("0.00") ){
								System.out.println(value.getJSONObject("labels").getString("io.kubernetes.pod.name")  + " cpu usge === 0 ");
							}
							
							String mem =  getMemUsage(obj1.getJSONObject("memory").getLong("usage")) ;
							Util.m.put(value.getJSONObject("labels").getString("io.kubernetes.pod.name"),cpu+"@"+mem) ;
						}
					}
					
				}
				// drop k8s_POD container info
			}
			
		 } // end for 
			
		}
//		System.out.println(f.format(new Date(System.currentTimeMillis())).toString()+ "  " + Thread.currentThread().getName()) ; 
		
	}
	
	
	public static void Test(String node){

//		long t = System.currentTimeMillis() ;
//		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//		System.out.println(f.format(new Date(t)).toString() + "" + Thread.currentThread().getName());
//		Set<String>  keys = new HashSet<String>() ; 

		JSONObject  jobj = getResult(String.format(CADVISOR_API, node)) ;
		Iterator iterator = jobj.keys();
		while (iterator.hasNext()) {
			String key = (String) iterator.next();
			JSONObject value = jobj.getJSONObject(key);
//			System.out.println(key);
			if(key.toString().indexOf("user") < 0 ){
				continue  ;
			}
			
			if (null != value.getJSONObject("labels").get("io.kubernetes.container.name") && ( 
					value.getJSONObject("labels").get("io.kubernetes.container.name").toString().equals("POD") || 
					value.getJSONObject("labels").get("io.kubernetes.pod.name").toString().indexOf("paas-agent") != -1)) {
				continue;
			}else{
				if (null != value.getJSONObject("labels").get("io.kubernetes.pod.name") && value.getJSONObject("labels").get("io.kubernetes.pod.name").toString().trim().length() > 1) {
					
//					keys.add( value.getJSONObject("labels").get("io.kubernetes.pod.name").toString()) ;
					
//					System.out.println(" ---------------------- " +  value.getJSONObject("labels").get("io.kubernetes.pod.name").toString() );
//					System.out.println(value.getJSONObject("labels").getString("io.kubernetes.pod.name"));
					
					JSONArray jsonArray = value.getJSONArray("stats");
					JSONObject obj1 = jsonArray.getJSONObject(jsonArray.length() -1 ) ;
//					System.out.println(obj1.get("timestamp"));
				
					
					JSONObject obj2 = jsonArray.getJSONObject(jsonArray.length() -2 ) ;
					
//					System.out.println(obj2.get("timestamp"));
//					System.out.println(obj2.getJSONObject("memory").get("usage")) ;
//					System.out.println(obj2.getJSONObject("cpu").getJSONObject("usage").get("total")) ;
					
					Long time = (TimetoLong(obj1.get("timestamp").toString()) - TimetoLong(obj2.get("timestamp").toString())) ; 
//					System.out.println(" time  is " +time ) ;
					

					
					
					String cpu = getCpuUsgae(obj1.getJSONObject("cpu").getJSONObject("usage").getLong("total"),
							obj2.getJSONObject("cpu").getJSONObject("usage").getLong(("total")),
							time,value.getJSONObject("spec").getJSONObject("cpu").getInt("limit")) ;
					
					System.out.println(value.getJSONObject("labels").getString("io.kubernetes.pod.name"));
					System.out.println(obj1.getJSONObject("memory").get("usage")) ;
					System.out.println(obj1.getJSONObject("cpu").getJSONObject("usage").get("total")) ;
					
					
					if(cpu.equals("0.00") ){
//						System.out.println(obj1.getJSONObject("memory").get("usage")) ;
//						System.out.println(obj1.getJSONObject("cpu").getJSONObject("usage").get("total")) ;
						
						
//						System.out.println(" cpu1,cpu2 = " +Cpu1 + "," + Cpu2 );

//						System.out.println(" cpu1 - cpu2  = "+ (Cpu1  - Cpu2 ) + " time = " + time  );
						
					}
					
					
					String mem =  getMemUsage(obj1.getJSONObject("memory").getLong("usage")) ;
					m.put(value.getJSONObject("labels").getString("io.kubernetes.pod.name"),cpu+"@"+mem) ;
					
				}
				
			}
			// drop k8s_POD container info
		}
//		System.out.println(f.format(new Date(System.currentTimeMillis())).toString() + " " + Thread.currentThread().getName() ) ; 
		
		
		
		
	}
	
	
	public static void main(String[] args) {

		// Timestamp ts = new Timestamp(System.currentTimeMillis());
		// System.out.println(getT("2017-10-31T06:28:26Z"));
		// ts = Timestamp.valueOf(getT("2017-10-31T06:28:26Z"));
		// System.out.println(ts);
		// Util.getinfo();

		// getinfo();
		// Util.toSting() ;
		
//		Util.server  = "10.2.33.10:8080"  ;
//		getNode();
//		
//		Util.nodelist.add("1");
//		Util.nodelist.add("2");
//		Util.nodelist.add("3");
//		Util.nodelist.add("4");
//		Util.nodelist.add("5");
//		Util.nodelist.add("6");
//		Util.nodelist.add("7");

	

		
//		Test("10.2.33.14")  ;
		
//		
//		Set<String>  ss  =  Util.m.keySet()  ;
//		System.out.println(" resut" + ss.size());
//
//		for (String s : ss) {
//			System.out.println(s  + "=====" + m.get(s));
//		}
//		System.out.println(" resut2");
		
//		getinfo();
//		
//		getNameSpace();
		
		
		
	}

}
