package io.compgen.ngsutils.vcf.support;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

import io.compgen.common.cache.Cache;
import io.compgen.common.cache.FileBackedCache;
import io.compgen.common.cache.LRUCache;
import io.compgen.common.cache.TieredCache;

public class EnsemblREST {
	private static final Map<String, EnsemblREST> servers = new HashMap<String, EnsemblREST>();
	private static String cacheFile = System.getProperty("user.home") + File.pathSeparator + ".ensembl_rest.cache";
	
	public static EnsemblREST getServer() throws IOException {
		return getServer(null);
	}
	public static EnsemblREST getServer(String hostname) throws IOException {
		if (hostname == null) {
			hostname = "http://rest.ensembl.org";
		}
		if (!servers.containsKey(hostname)) {
			servers.put(hostname, new EnsemblREST(hostname));
		}
		return servers.get(hostname);
	}
	
	public static void cleanup() {
		for (EnsemblREST server: servers.values()) {
			server.close();
		}
	}


	protected final String hostname;
	protected final Cache<String, String> cache;
	protected final FileBackedCache<String, String> fileCache;
	protected long rateLimitWaitUntil = 0;
	
	protected List<String> rateLimitHeaders = new ArrayList<String>();
	
	public EnsemblREST(String hostname) throws IOException {
		this.hostname = hostname;
		this.fileCache = new FileBackedCache<String,String>(new File(cacheFile), true, true);
		this.cache = new TieredCache<String,String>(
						new LRUCache<String, String>(10000), 
						this.fileCache
						);
	}

	public void close() {
		System.err.println("Last rate limiting headers: "+hostname);
		for (String s: rateLimitHeaders) {
			System.err.println(s);
		}
		try {
			fileCache.close();
		} catch (IOException e) {
		}
	}
	
	public JsonValue call(String uri) throws IOException {
		return call(uri, -1);
	}
	
	public JsonValue call(String uri, int timeoutSecs) throws IOException {
		if (cache.containsKey(uri)) {
			return Json.parse(cache.get(uri));
		}

		long startMillis = System.currentTimeMillis();
		
		while (true) {
			long currentMillis = System.currentTimeMillis();
			if (currentMillis < rateLimitWaitUntil) {
				if (timeoutSecs > 0 && (rateLimitWaitUntil - currentMillis) > (timeoutSecs*1000)) {
					throw new IOException("Currently retry-limited until: "+new Date(rateLimitWaitUntil).toString()+", which is later than timeout: "+timeoutSecs+" sec");
				}
				try {
					System.err.println("Currently retry-limited until: "+new Date(rateLimitWaitUntil).toString()+"... sleeping");
					Thread.sleep((rateLimitWaitUntil - currentMillis) + 1000); // wait for an extra second
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
			}

			if ((System.currentTimeMillis() - startMillis) > (timeoutSecs * 1000)) {
				throw new IOException("Timeout...");
			}
			
		    URL url = new URL(hostname+"/"+uri);
		 
		    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		    conn.setRequestProperty("Content-Type", "application/json");
		 
		    InputStream response = conn.getInputStream();
		    int responseCode = conn.getResponseCode();
		 
		    if (responseCode == 429) {
		    	System.err.println("Exceeded REST API rate limits... sleeping");

		    	for (Entry<String, List<String>> kv: conn.getHeaderFields().entrySet()) {
		    		for (String v: kv.getValue()){
			    		System.err.println(kv.getKey()+": "+v);
		    		}
		    	}

		    	// rate limited
		    	String retry = conn.getHeaderField("Retry-After");
		    	Double retrySecs = Double.parseDouble(retry);
		    	
		    	rateLimitWaitUntil = (long) (System.currentTimeMillis() + (retrySecs * 1000));
		    	continue;
		    }
		    
		    rateLimitWaitUntil = 0; // must not be limited...
		    
		    if(responseCode != 200) {
				throw new IOException("Unexpected HTTP status code: "+responseCode);
		    }
		 
		    rateLimitHeaders.clear();
	    	for (Entry<String, List<String>> kv: conn.getHeaderFields().entrySet()) {
    			if (kv.getKey() != null && kv.getKey().startsWith("X-RateLimit-")) {
    				for (String v: kv.getValue()) {
    					rateLimitHeaders.add(kv.getKey() +": "+v);
    				}
    			}
	    	}
		    
		    String output = null;
		    Reader reader = null;
		    try {
		      reader = new BufferedReader(new InputStreamReader(response, "UTF-8"));
		      StringBuilder builder = new StringBuilder();
		      char[] buffer = new char[8192];
		      int read;
		      while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
		        builder.append(buffer, 0, read);
		      }
		      output = builder.toString();
		    } 
		    finally {
		        if (reader != null) try {
		          reader.close(); 
		        } catch (IOException logOrIgnore) {
		          logOrIgnore.printStackTrace();
		        }
		    }
		 
		    if (output != null) {
//		    	System.err.println("Saving in cache: " + uri);
		    	cache.put(uri, output);
		    }
			return Json.parse(output);
		}
	  }
	public static void setCache(String cacheFile) {
		EnsemblREST.cacheFile = cacheFile;
	}

}
