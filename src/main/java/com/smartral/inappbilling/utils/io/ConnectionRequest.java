/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores
 * CA 94065 USA or visit www.oracle.com if you need additional information or
 * have any questions.
 */

package com.smartral.inappbilling.utils.io;


import com.smartral.inappbilling.utils.ui.events.ActionEvent;
import com.smartral.inappbilling.utils.ui.events.ActionListener;
import com.smartral.inappbilling.utils.util.StringUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>This class represents a connection object in the form of a request response
 * typically common for HTTP/HTTPS connections. A connection request is added to
 * the {@link com.codename1.io.NetworkManager} for processing in a queue on one of the
 * network threads. You can read more about networking in Codename One {@link com.codename1.io here}</p>
 * 
 * <p>The sample
 * code below fetches a page of data from the nestoria housing listing API.<br>
 * You can see instructions on how to display the data in the {@link com.codename1.components.InfiniteScrollAdapter}
 * class. You can read more about networking in Codename One {@link com.codename1.io here}.</p>
 * <script src="https://gist.github.com/codenameone/22efe9e04e2b8986dfc3.js"></script>
 *
 * @author Shai Almog
 */
public class ConnectionRequest {
    
    private Executor mainExecutor;
    private Executor networkExecutor;
    private Hashtable cookies;
    private static ThreadLocal<Hashtable> threadCookies;
    
    public static void setThreadCookieJar(Hashtable cookies) {
        if (threadCookies == null) {
            threadCookies = new ThreadLocal<Hashtable>();
            
        }
        threadCookies.set(cookies);
    }
    
    public static Hashtable getThreadCookieJar() {
        if (threadCookies == null) {
            threadCookies = new ThreadLocal<Hashtable>();
            
        }
        if (threadCookies.get() == null) {
            threadCookies.set(new Hashtable());
        }
        return threadCookies.get();
            
    }
    
    public void setCookieJar(Hashtable cookies) {
        this.cookies = cookies;
    }
    
    public Hashtable getCookies() {
        return cookies;
    }
    
    
    private class CodenameOneImplementation {

        private HttpURLConnection c(Object connection) {
            return (HttpURLConnection)connection;
        }
        
        private void setPostRequest(Object connection, boolean post) {
            ((HttpURLConnection)connection).setDoInput(post);
            try {
                ((HttpURLConnection)connection).setRequestMethod(post ? "POST" : "GET");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private void setHeader(Object connection, String key, String value) {
            HttpURLConnection c = (HttpURLConnection)connection;
            c.addRequestProperty(key, value);
        }

        private void setChunkedStreamingMode(Object connection, int chunkedStreamingLen) {
            c(connection).setChunkedStreamingMode(chunkedStreamingLen);
        }

        private Object connect(String actualUrl, boolean readRequest, boolean writeRequest, int timeout) throws IOException {
            try {
                HttpURLConnection conn = (HttpURLConnection)(new URL(actualUrl)).openConnection();
                conn.setDoInput(readRequest);
                conn.setDoOutput(writeRequest);
                conn.setConnectTimeout(timeout);
                return conn;
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        }

        private Object connect(String actualUrl, boolean readRequest, boolean writeRequest) throws IOException {
            return connect(actualUrl, readRequest, writeRequest, 5000);
        }

        private void setHttpMethod(Object connection, String httpMethod) {
            try {
                c(connection).setRequestMethod(httpMethod);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        /**
        * Returns the cookies for this URL
        * 
        * @param url the url on which we are checking for cookies
        * @return the cookies to submit to the given URL
        */
       public Vector getCookiesForURL(String url) {
           Vector response = null;
           //if (Cookie.isAutoStored()) {
           //    cookies = (Hashtable) Storage.getInstance().readObject(Cookie.STORAGE_NAME);
           //}

           String protocol = "";
           int pos = -1;
           if ( (pos = url.indexOf(":")) >= 0 ){
               protocol = url.substring(0, pos);
           }
           boolean isHttp = ("http".equals(protocol) || "https".equals(protocol) );
           boolean isSecure = "https".equals(protocol);
           String path = getURLPath(url);


           if(cookies != null && cookies.size() > 0) {
               String domain = getURLDomain(url);
               Enumeration e = cookies.keys();
               while (e.hasMoreElements()) {
                   String domainKey = (String) e.nextElement();
                   if (domain.indexOf(domainKey) > -1) {
                       Hashtable h = (Hashtable) cookies.get(domainKey);
                       if (h != null) {
                           Enumeration enumCookies = h.elements();
                           if(response == null){
                               response = new Vector();
                           }
                           while (enumCookies.hasMoreElements()) {
                               Cookie nex = (Cookie)enumCookies.nextElement();
                               if ( nex.isHttpOnly() && !isHttp ){
                                   continue;
                               }
                               if ( nex.isSecure() && !isSecure ){
                                   continue;
                               }
                               if ( path.indexOf(nex.getPath()) != 0 ){
                                   continue;
                               }
                               response.addElement(nex);
                           }
                       }
                   }
               }
           }
           return response;
       }

        private OutputStream openOutputStream(Object connection) throws IOException {
            return c(connection).getOutputStream();
        }

        private int getResponseCode(Object connection) throws IOException {
            return c(connection).getResponseCode();
        }

        private String[] getHeaderFields(String name, Object connection) {
            HttpURLConnection c = (HttpURLConnection) connection;
            List<String> headers = new ArrayList<String>();

            // we need to merge headers with differing case since this should be case insensitive
            for(String key : c.getHeaderFields().keySet()) {
                if(key != null && key.equalsIgnoreCase(name)) {
                    headers.addAll(c.getHeaderFields().get(key));
                }
            }
            if (headers.size() > 0) {
                List<String> v = new ArrayList<String>();
                v.addAll(headers);
                Collections.reverse(v);
                String[] s = new String[v.size()];
                v.toArray(s);
                return s;
            }
            return null;
        }

        public void addCookie(Cookie [] cookiesArray) {
            if(cookies == null){
                cookies = getThreadCookieJar();
            }
            int calen = cookiesArray.length;
            for (int i = 0; i < calen; i++) {
                Cookie cookie = cookiesArray[i];
                Hashtable h = (Hashtable)cookies.get(cookie.getDomain());
                if(h == null){
                    h = new Hashtable();
                    cookies.put(cookie.getDomain(), h);
                }
                h.put(cookie.getName(), cookie);
            }

            //if(Cookie.isAutoStored()){
            //    if(Storage.getInstance().exists(Cookie.STORAGE_NAME)){
            //        Storage.getInstance().deleteStorageFile(Cookie.STORAGE_NAME);
            //    }
            //    Storage.getInstance().writeObject(Cookie.STORAGE_NAME, cookies);
            //}
        }

        /**
         * Adds/replaces a cookie to be sent to the given domain
         * 
         * @param c cookie to add
         */
        public void addCookie(Cookie c) {
            if(cookies == null){
                cookies = getThreadCookieJar();
            }
            Hashtable h = (Hashtable)cookies.get(c.getDomain());
            if(h == null){
                h = new Hashtable();
                cookies.put(c.getDomain(), h);
            }
            h.put(c.getName(), c);
            //if(Cookie.isAutoStored()){
            //    if(Storage.getInstance().exists(Cookie.STORAGE_NAME)){
            //        Storage.getInstance().deleteStorageFile(Cookie.STORAGE_NAME);
            //    }
            //    Storage.getInstance().writeObject(Cookie.STORAGE_NAME, cookies);
            //}
        }

        private String getHeaderField(String location, Object connection) {
            return c(connection).getHeaderField(location);
        }

        private void cleanup(Object output) {
            try {
                if (output instanceof InputStream) {
                    ((InputStream)output).close();
                } else if (output instanceof OutputStream) {
                    ((OutputStream)output).close();
                }
            } catch (Exception ex) {
                Log.e(ex);
            }
        }

        private String getResponseMessage(Object connection) throws IOException {
            return c(connection).getResponseMessage();
        }

        private int getContentLength(Object connection) {
            return c(connection).getContentLength();
        }

        private InputStream openInputStream(Object connection) throws IOException {
            if(connection instanceof HttpURLConnection) {
                HttpURLConnection ht = (HttpURLConnection)connection;
                if(ht.getResponseCode() < 400) {
                    return new BufferedInputStream(ht.getInputStream());
                }
                return new BufferedInputStream(ht.getErrorStream());
            } else {
                return new BufferedInputStream(((URLConnection) connection).getInputStream());
            }   
        }

        private String[] getHeaderFieldNames(Object connection) {
            Set<String> s = ((HttpURLConnection) connection).getHeaderFields().keySet();
            String[] resp = new String[s.size()];
            s.toArray(resp);
            return resp;
        }

        private String getURLDomain(String url) {
            try {
                return new URL(url).getHost();
            } catch (Exception ex) {
                return "";
            }
        }

        private String getURLPath(String url) {
            try {
                return new URL(url).getPath();
            } catch (Exception ex) {
                return "";
            }
                   
        }

        private boolean shouldWriteUTFAsGetBytes() {
            return true;
        }

        private void setUseNativeCookieStore(boolean b) {
            //return false;
        }
        
    }
    
    private CodenameOneImplementation newImplementation() {
        return new CodenameOneImplementation();
    }
    CodenameOneImplementation impl;
    private static class Util {
        
        static CodenameOneImplementation getImplementation(ConnectionRequest req) {
            if (req.impl == null) {
                req.impl = req.newImplementation();
            }
            return req.impl;
        }

        private static String relativeToAbsolute(String url, String uri) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        private static void copy(InputStream input, OutputStream o) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        private static void cleanup(OutputStream o) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
        private static String encodeBody(String input) {
            return input;
        }
        
        private static String encodeBody(byte[] input) {
            return null;
        }
        
        private static String encodeUrl(String input) {
            return input;
        }
        
        private static String encodeUrl(byte[] input) {
            return null;
        }
    }
    
    /**
     * A critical priority request will "push" through the queue to the highest point
     * regardless of anything else and ignoring anything that is not in itself of
     * critical priority.
     * A critical priority will stop any none critical connection in progress
     */
    public static final byte PRIORITY_CRITICAL = (byte)100;

    /**
     * A high priority request is the second highest level, it will act exactly like
     * a critical priority with one difference. It doesn't block another incoming high priority
     * request. E.g. if a high priority request
     */
    public static final byte PRIORITY_HIGH = (byte)80;

    /**
     * Normal priority executes as usual on the queue
     */
    public static final byte PRIORITY_NORMAL = (byte)50;

    /**
     * Low priority requests are mostly background tasks that should still be accomplished though
     */
    public static final byte PRIORITY_LOW = (byte)30;

    /**
     * Redundant elements can be discarded from the queue when paused
     */
    public static final byte PRIORITY_REDUNDANT = (byte)0;

    /**
     * Workaround for https://bugs.php.net/bug.php?id=65633 allowing developers to
     * customize the name of the cookie header to Cookie
     * @return the cookieHeader
     */
    public static String getCookieHeader() {
        return cookieHeader;
    }

    /**
     * Workaround for https://bugs.php.net/bug.php?id=65633 allowing developers to
     * customize the name of the cookie header to Cookie
     * @param aCookieHeader the cookieHeader to set
     */
    public static void setCookieHeader(String aCookieHeader) {
        cookieHeader = aCookieHeader;
    }

    /**
     * @return the cookiesEnabledDefault
     */
    public static boolean isCookiesEnabledDefault() {
        return cookiesEnabledDefault;
    }

    /**
     * @param aCookiesEnabledDefault the cookiesEnabledDefault to set
     */
    public static void setCookiesEnabledDefault(boolean aCookiesEnabledDefault) {
        if(!aCookiesEnabledDefault) {
            setUseNativeCookieStore(false);
        }
        cookiesEnabledDefault = aCookiesEnabledDefault;
    }


    /**
     * Enables/Disables automatic redirects globally and returns the 302 error code, <strong>IMPORTANT</strong>
     * this feature doesn't work on all platforms and currently doesn't work on iOS which always implicitly redirects
     * @return the defaultFollowRedirects
     */
    public static boolean isDefaultFollowRedirects() {
        return defaultFollowRedirects;
    }

    /**
     * Enables/Disables automatic redirects globally and returns the 302 error code, <strong>IMPORTANT</strong>
     * this feature doesn't work on all platforms and currently doesn't work on iOS which always implicitly redirects
     * @param aDefaultFollowRedirects the defaultFollowRedirects to set
     */
    public static void setDefaultFollowRedirects(boolean aDefaultFollowRedirects) {
        defaultFollowRedirects = aDefaultFollowRedirects;
    }

    private byte priority = PRIORITY_NORMAL;
    private long timeSinceLastUpdate;
    private Hashtable requestArguments;

    private boolean post = true;
    private String contentType = "application/x-www-form-urlencoded; charset=UTF-8";
    private static String defaultUserAgent = null;
    private String userAgent = getDefaultUserAgent();
    private String url;
    private boolean writeRequest;
    private boolean readRequest = true;
    private boolean paused;
    private boolean killed = false;
    private static boolean defaultFollowRedirects = true;
    private boolean followRedirects = defaultFollowRedirects;
    private int timeout = -1;
    private InputStream input;
    private OutputStream output;
    private int progress = NetworkEvent.PROGRESS_TYPE_OUTPUT;
    private int contentLength = -1;
    private boolean duplicateSupported = true;
    private Hashtable userHeaders;

    private byte[] data;
    private int responseCode;
    private String httpMethod;
    private int silentRetryCount = 0;
    private boolean failSilently;
    boolean retrying;
    private boolean readResponseForErrors;
    private String responseContentType;
    private boolean redirecting;
    private static boolean cookiesEnabledDefault = true;
    private boolean cookiesEnabled = cookiesEnabledDefault;
    private int chunkedStreamingLen = -1;
    private Exception failureException;
    private int failureErrorCode;
    private String destinationFile;
    private String destinationStorage;
    
    // Flag to indicate if the contentType was explicitly set for this 
    // request
    private boolean contentTypeSetExplicitly;
    
    /**
     * Workaround for https://bugs.php.net/bug.php?id=65633 allowing developers to
     * customize the name of the cookie header to Cookie
     */
    private static String cookieHeader = "cookie";
    
    /**
     * Default constructor
     */
    public ConnectionRequest() {
        cookies = getThreadCookieJar(); // default so that requests originating from
                // the same thread have the same cookies
        //if(NetworkManager.getInstance().isAPSupported()) {
        //    silentRetryCount = 1;
        //}
    }

    /**
     * Construct a connection request to a url
     * 
     * @param url the url
     */
    public ConnectionRequest(String url) {
        this();
        setUrl(url);
    }
    

    /**
     * Construct a connection request to a url
     * 
     * @param url the url
     * @param post whether the request is a post url or a get URL
     */
    public ConnectionRequest(String url, boolean post) {
        this(url);
        setPost(post);
    }
    
    /**
     * This method will return a valid value for only some of the responses and only after the response was processed
     * @return null or the actual data returned
     */
    public byte[] getResponseData() {
        return data;
    }
    
    
    /**
     * Sets the http method for the request
     * @param httpMethod the http method string
     */
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    } 
    
    /**
     * Returns the http method 
     * @return the http method of the request
     */
    public String getHttpMethod() {
        return httpMethod;
    }
    
    /**
     * Adds the given header to the request that will be sent
     * 
     * @param key the header key
     * @param value the header value
     */
    public void addRequestHeader(String key, String value) {
        if(userHeaders == null) {
            userHeaders = new Hashtable();
        }
        if(key.equalsIgnoreCase("content-type")) {
            setContentType(value);
        } else {
            userHeaders.put(key, value);
        }
    }

    /**
     * Adds the given header to the request that will be sent unless the header
     * is already set to something else
     *
     * @param key the header key
     * @param value the header value
     */
    void addRequestHeaderDontRepleace(String key, String value) {
        if(userHeaders == null) {
            userHeaders = new Hashtable();
        }
        if(!userHeaders.containsKey(key)) {
            userHeaders.put(key, value);
        }
    }

    void prepare() {
        timeSinceLastUpdate = System.currentTimeMillis();
    }
    
    

    /**
     * Invoked to initialize HTTP headers, cookies etc. 
     * 
     * @param connection the connection object
     */
    protected void initConnection(Object connection) {
        timeSinceLastUpdate = System.currentTimeMillis();
        CodenameOneImplementation impl = Util.getImplementation(this);
        impl.setPostRequest(connection, isPost());

        if(getUserAgent() != null) {
            impl.setHeader(connection, "User-Agent", getUserAgent());
        }

        if (getContentType() != null) {
            // UWP will automatically filter out the Content-Type header from GET requests
            // Historically, CN1 has always included this header even though it has no meaning
            // for GET requests.  it would be be better if CN1 did not include this header 
            // with GET requests, but for backward compatibility, I'll leave it on as
            // the default, and add a property to turn it off.
            //  -- SJH Sept. 15, 2016
            boolean shouldAddContentType = contentTypeSetExplicitly || 
                    System.getProperty("ConnectionRequest.excludeContentTypeFromGetRequests", "true").equals("false");

            if (isPost() || (getHttpMethod() != null && !"get".equals(getHttpMethod().toLowerCase()))) {
                shouldAddContentType = true;
            }

            if(shouldAddContentType) {
                impl.setHeader(connection, "Content-Type", getContentType());
            }
        }
        
        if(chunkedStreamingLen > -1){
            impl.setChunkedStreamingMode(connection, chunkedStreamingLen);
        }

        if(userHeaders != null) {
            Enumeration e = userHeaders.keys();
            while(e.hasMoreElements()) {
                String k = (String)e.nextElement();
                String value = (String)userHeaders.get(k);
                impl.setHeader(connection, k, value);
            }
        }
    }

    /**
     * Performs the actual network request on behalf of the network manager
     */
    void performOperation() throws IOException {
        if(shouldStop()) {
            return;
        }
        CodenameOneImplementation impl = Util.getImplementation(this);
        Object connection = null;
        input = null;
        output = null;
        redirecting = false;
        try {
            String actualUrl = createRequestURL();
            if(timeout > 0) {
                connection = impl.connect(actualUrl, isReadRequest(), isPost() || isWriteRequest(), timeout);
            } else {
                connection = impl.connect(actualUrl, isReadRequest(), isPost() || isWriteRequest());
            }
            if(shouldStop()) {
                return;
            }
            initConnection(connection);
            if(httpMethod != null) {
                impl.setHttpMethod(connection, httpMethod);
            }
            Vector v = impl.getCookiesForURL(actualUrl);
            if(v != null) {
                int c = v.size();
                if(c > 0) {
                    StringBuilder cookieStr = new StringBuilder();
                    Cookie first = (Cookie)v.elementAt(0);
                    cookieSent(first);
                    cookieStr.append(first.getName());
                    cookieStr.append("=");
                    cookieStr.append(first.getValue());
                    for(int iter = 1 ; iter < c ; iter++) {
                        Cookie current = (Cookie)v.elementAt(iter);
                        cookieStr.append(";");
                        cookieStr.append(current.getName());
                        cookieStr.append("=");
                        cookieStr.append(current.getValue());
                        cookieSent(current);
                    }
                    impl.setHeader(connection, cookieHeader, initCookieHeader(cookieStr.toString()));
                } else {
                    String s = initCookieHeader(null);
                    if(s != null) {
                        impl.setHeader(connection, cookieHeader, s);
                    }
                }
            } else {
                String s = initCookieHeader(null);
                if(s != null) {
                    impl.setHeader(connection, cookieHeader, s);
                }
            }
            if(isWriteRequest()) {
                progress = NetworkEvent.PROGRESS_TYPE_OUTPUT;
                output = impl.openOutputStream(connection);
                if(shouldStop()) {
                    return;
                }
                //if(NetworkManager.getInstance().hasProgressListeners() && output instanceof BufferedOutputStream) {
                //    ((BufferedOutputStream)output).setProgressListener(this);
                //}
                buildRequestBody(output);
                if(shouldStop()) {
                    return;
                }
                if(output instanceof BufferedOutputStream) {
                    ((BufferedOutputStream)output).flush();
                    if(shouldStop()) {
                        return;
                    }
                }
            }
            timeSinceLastUpdate = System.currentTimeMillis();
            responseCode = impl.getResponseCode(connection);

            if(isCookiesEnabled()) {
                String[] cookies = impl.getHeaderFields("Set-Cookie", connection);
                if(cookies != null && cookies.length > 0){
                    Vector cook = new Vector();
                    int clen = cookies.length;
                    for(int iter = 0 ; iter < clen ; iter++) {
                        Cookie coo = parseCookieHeader(cookies[iter]);
                        if(coo != null) {
                            cook.addElement(coo);
                            cookieReceived(coo);
                        }
                    }
                    Cookie [] arr = new Cookie[cook.size()];
                    int arlen = arr.length;
                    for (int i = 0; i < arlen; i++) {
                        arr[i] = (Cookie) cook.elementAt(i);
                    }
                    impl.addCookie(arr);
                }
            }
            
            if(responseCode - 200 < 0 || responseCode - 200 > 100) {
                readErrorCodeHeaders(connection);
                // redirect to new location
                if(followRedirects && (responseCode == 301 || responseCode == 302
                        || responseCode == 303)) {
                    String uri = impl.getHeaderField("location", connection);

                    if(!(uri.startsWith("http://") || uri.startsWith("https://"))) {
                        // relative URI's in the location header are illegal but some sites mistakenly use them
                        url = Util.relativeToAbsolute(url, uri);
                    } else {
                        url = uri;
                    }
                    if(requestArguments != null && url.indexOf('?') > -1) {
                        requestArguments.clear();
                    }
                    
                    if((responseCode == 302 || responseCode == 303)){
                        if(this.post && shouldConvertPostToGetOnRedirect()) {
                            this.post = false;
                            setWriteRequest(false);
                        }
                    }

                    impl.cleanup(output);
                    impl.cleanup(connection);
                    connection = null;
                    output = null;
                    if(!onRedirect(url)){
                        redirecting = true;
                        retry();
                    }
                    return;
                }

                handleErrorResponseCode(responseCode, impl.getResponseMessage(connection));
                if(!isReadResponseForErrors()) {
                    return;
                }
            }
            responseContentType = getHeader(connection, "Content-Type");
            readHeaders(connection);
            contentLength = impl.getContentLength(connection);
            timeSinceLastUpdate = System.currentTimeMillis();
            
            progress = NetworkEvent.PROGRESS_TYPE_INPUT;
            if(isReadRequest()) {
                input = impl.openInputStream(connection);
                if(shouldStop()) {
                    return;
                }
                readResponse(input);
                if(shouldAutoCloseResponse()) {
                    input.close();
                }
                input = null;
            }
        } finally {
            // always cleanup connections/streams even in case of an exception
            impl.cleanup(output);
            impl.cleanup(input);
            impl.cleanup(connection);
            timeSinceLastUpdate = -1;
            input = null;
            output = null;
            connection = null;
        }
        if(!isKilled()) {
            
            callSerially(new Runnable() {
                public void run() {
                    postResponse();
                }
            });
        }
    }
    
    private void callSerially(Runnable r) {
        if (mainExecutor != null) {
            mainExecutor.execute(r);
        } else {
            r.run();
        }
    }
    
    /**
     * Callback invoked for every cookie received from the server
     * @param c the cookie
     */
    protected void cookieReceived(Cookie c) {
    }

    /**
     * Callback invoked for every cookie being sent to the server
     * @param c the cookie
     */
    protected void cookieSent(Cookie c) {
    }
    
    /**
     * Allows subclasses to inject cookies into the request
     * @param cookie the cookie that the implementation is about to send or null for no cookie
     * @return new cookie or the value of cookie
     */
    protected String initCookieHeader(String cookie) {
        return cookie;
    }

    /**
     * Returns the response code for this request, this is only relevant after the request completed and
     * might contain a temporary (e.g. redirect) code while the request is in progress
     * @return the response code
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Returns the response code for this request, this is only relevant after the request completed and
     * might contain a temporary (e.g. redirect) code while the request is in progress
     * @return the response code
     * @deprecated misspelled method name please use getResponseCode
     */
    public int getResposeCode() {
        return responseCode;
    }
    
    /**
     * This mimics the behavior of browsers that convert post operations to get operations when redirecting a
     * request.
     * @return defaults to true, this case be modified by subclasses
     */
    protected boolean shouldConvertPostToGetOnRedirect() {
        return true;
    }

    /**
     * Allows reading the headers from the connection by calling the getHeader() method. 
     * @param connection used when invoking getHeader
     * @throws IOException thrown on failure
     */
    protected void readHeaders(Object connection) throws IOException {
    }

    /**
     * Allows reading the headers from the connection by calling the getHeader() method when a response that isn't 200 OK is sent. 
     * @param connection used when invoking getHeader
     * @throws IOException thrown on failure
     */
    protected void readErrorCodeHeaders(Object connection) throws IOException {
    }

    /**
     * Returns the HTTP header field for the given connection, this method is only guaranteed to work
     * when invoked from the readHeaders method.
     *
     * @param connection the connection to the network
     * @param header the name of the header
     * @return the value of the header
     * @throws IOException thrown on failure
     */
    protected String getHeader(Object connection, String header) throws IOException {
        return Util.getImplementation(this).getHeaderField(header, connection);
    }

    /**
     * Returns the HTTP header field for the given connection, this method is only guaranteed to work
     * when invoked from the readHeaders method. Unlike the getHeader method this version works when
     * the same header name is declared multiple times.
     *
     * @param connection the connection to the network
     * @param header the name of the header
     * @return the value of the header
     * @throws IOException thrown on failure
     */
    protected String[] getHeaders(Object connection, String header) throws IOException {
        return Util.getImplementation(this).getHeaderFields(header, connection);
    }

    /**
     * Returns the HTTP header field names for the given connection, this method is only guaranteed to work
     * when invoked from the readHeaders method.
     *
     * @param connection the connection to the network
     * @return the names of the headers
     * @throws IOException thrown on failure
     */
    protected String[] getHeaderFieldNames(Object connection) throws IOException {
        return Util.getImplementation(this).getHeaderFieldNames(connection);
    }
    
    /**
     * Returns the amount of time to yield for other processes, this is an implicit 
     * method that automatically generates values for lower priority connections
     * @return yield duration or -1 for no yield
     */
    protected int getYield() {
        if(priority > PRIORITY_NORMAL) {
            return -1;
        }
        if(priority == PRIORITY_NORMAL) {
            return 20;
        }
        return 40;
    }

    /**
     * Indicates whether the response stream should be closed automatically by
     * the framework (defaults to true), this might cause an issue if the stream
     * needs to be passed to a separate thread for reading.
     * 
     * @return true to close the response stream automatically.
     */
    protected boolean shouldAutoCloseResponse() {
        return true;
    }

    /**
     * Parses a raw cookie header and returns a cookie object to send back at the server
     * 
     * @param h raw cookie header
     * @return the cookie object
     */
    @SuppressWarnings("deprecation")
    private Cookie parseCookieHeader(String h) {
        String lowerH = h.toLowerCase();
        
        Cookie c = new Cookie();
        int edge = h.indexOf(';');
        int equals = h.indexOf('=');
        if(equals < 0) {
            return null;
        }
        c.setName(h.substring(0, equals));
        if(edge < 0) {
            c.setValue(h.substring(equals + 1));
            c.setDomain(Util.getImplementation(this).getURLDomain(url));
            return c;
        }else{
            c.setValue(h.substring(equals + 1, edge));
        }
        
        int index = lowerH.indexOf("domain=");
        if (index > -1) {
            String domain = h.substring(index + 7);
            index = domain.indexOf(';');
            if (index!=-1) {
                domain = domain.substring(0, index);
            }

            if (!url.contains(domain)) { //if (!hc.getHost().endsWith(domain)) {
                Logger.getLogger(getClass().getSimpleName()).log(Level.INFO, "Warning: Cookie tried to set to another domain");
                c.setDomain(Util.getImplementation(this).getURLDomain(url));
            } else {
                c.setDomain(domain);
            }
        } else {
            c.setDomain(Util.getImplementation(this).getURLDomain(url));
        }
        
        index = lowerH.indexOf("path=");
        if (index > -1) {
            String path = h.substring(index + 5);
            index = path.indexOf(';');
            if (index > -1) {
                path = path.substring(0, index);
            }
            
            if (Util.getImplementation(this).getURLPath(url).indexOf(path) != 0) { //if (!hc.getHost().endsWith(domain)) {
                Logger.getLogger(getClass().getSimpleName()).log(Level.INFO, "Warning: Cookie tried to set to another path");
                c.setPath(path);
            } else {
                // Don't set the path explicitly
            }
        } else {
            // Don't set the path explicitly
        }
        
        // Check for secure and httponly.
        // SJH NOTE:  It would be better to rewrite this whole method to 
        // split it up this way, rather than do the domain and path 
        // separately.. but this is a patch job to just get secure
        // path, and httponly working... don't want to break any existing
        // code for now.
        Vector parts = StringUtil.tokenizeString(lowerH, ';');
        for ( int i=0; i<parts.size(); i++){
            String part = (String) parts.elementAt(i);
            part = part.trim();
            if ( part.indexOf("secure") == 0 ){
                c.setSecure(true);
            } else if ( part.indexOf("httponly") == 0 ){
                c.setHttpOnly(true);
            }
        }
        
        

        return c;
    }

    /**
     * Handles IOException thrown when performing a network operation
     * 
     * @param err the exception thrown
     */
    protected void handleIOException(IOException err) {
        handleException(err);
    }

    /**
     * Handles an exception thrown when performing a network operation
     *
     * @param err the exception thrown
     */
    protected void handleRuntimeException(RuntimeException err) {
        handleException(err);
    }

    /**
     * Handles an exception thrown when performing a network operation, the default
     * implementation shows a retry dialog.
     *
     * @param err the exception thrown
     */
    protected void handleException(Exception err) {
        if(killed || failSilently) {
            failureException = err;
            return;
        }
        err.printStackTrace();
        if(silentRetryCount > 0) {
            silentRetryCount--;
            //NetworkManager.getInstance().resetAPN();
            retry();
            return;
        }
        //if(Display.isInitialized() && !Display.getInstance().isMinimized() &&
        //        Dialog.show("Exception", err.toString() + ": for URL " + url + "\n" + err.getMessage(), "Retry", "Cancel")) {
        //    retry();
        //} else {
            retrying = false;
            killed = true;
        //}
    }

    private List<ActionListener> responseCodeListeners = new ArrayList<ActionListener>();
    
    private void fireActionEvent(final List<ActionListener> listeners, final ActionEvent evt) {
        callSerially(new Runnable() {
            public void run() {
                for (ActionListener l : listeners) {
                    l.actionPerformed(evt);
                    if (evt.isConsumed()) {
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * Handles a server response code that is not 200 and not a redirect (unless redirect handling is disabled)
     *
     * @param code the response code from the server
     * @param message the response message from the server
     */
    protected void handleErrorResponseCode(int code, String message) {
        if(failSilently) {
            failureErrorCode = code;
            return;
        }
        if(responseCodeListeners != null) {
            if(!isKilled()) {
                NetworkEvent n = new NetworkEvent(this, code, message);
                fireActionEvent(responseCodeListeners, n);
            }
            return;
        }
        //if(Display.isInitialized() && !Display.getInstance().isMinimized() &&
        //        Dialog.show("Error", code + ": " + message, "Retry", "Cancel")) {
        //    retry();
        //} else {
            retrying = false;
            if(!isReadResponseForErrors()){
                killed = true;
            }
        //}
    }

    /**
     * Retry the current operation in case of an exception
     */
    public void retry() {
        retrying = true;
        addToQueue(this, true);
    }
    
    public void addToQueue() {
        addToQueue(this, true);
    }
    
    public void addToQueue(final ConnectionRequest req, boolean param) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    req.performOperation();
                } catch (Exception ex) {
                    handleException(ex);
                }
            }
        };
        if (networkExecutor != null) {
            networkExecutor.execute(r);
        } else {
            new Thread(r).start();
        }
    }

    /**
     * This is a callback method that been called when there is a redirect.
     * <strong>IMPORTANT</strong>
     * this feature doesn't work on all platforms and currently doesn't work on iOS which always implicitly redirects
     *
     * @param url the url to be redirected
     * @return true if the implementation would like to handle this by itself
     */
    public boolean onRedirect(String url){
        return false;
    }

    /**
     * Callback for the server response with the input stream from the server.
     * This method is invoked on the network thread
     * 
     * @param input the input stream containing the response
     * @throws IOException when a read input occurs
     */
    protected void readResponse(InputStream input) throws IOException  {
        if(isKilled()) {
            return;
        }
        if(destinationFile != null) {
            OutputStream o = new FileOutputStream(destinationFile);
            Util.copy(input, o);
            Util.cleanup(o);
            
            // was the download killed while we downloaded
            if(isKilled()) {
                new File(destinationFile).delete();
            }
        } //else {
//            if(destinationStorage != null) {
//                OutputStream o = Storage.getInstance().createOutputStream(destinationStorage);
//                Util.copy(input, o);
//                Util.cleanup(o);
//            
//                // was the download killed while we downloaded
//                if(isKilled()) {
//                    Storage.getInstance().deleteStorageFile(destinationStorage);
//                }
//            } else {
                data = inappbilling.utils.io.Util.readInputStream(input);
//            }
//        }
        if(hasResponseListeners() && !isKilled()) {
            fireResponseListener(new NetworkEvent(this, data));
        }
    }

    /**
     * A callback method that's invoked on the EDT after the readResponse() method has finished,
     * this is the place where developers should change their Codename One user interface to
     * avoid race conditions that might be triggered by modifications within readResponse.
     * Notice this method is only invoked on a successful response and will not be invoked in case
     * of a failure.
     */
    protected void postResponse() {
    }
    
    /**
     * Creates the request URL mostly for a get request
     * 
     * @return the string of a request
     */
    protected String createRequestURL() {
        if(!post && requestArguments != null) {
            StringBuilder b = new StringBuilder(url);
            Enumeration e = requestArguments.keys();
            if(e.hasMoreElements()) {
                b.append("?");
            }
            while(e.hasMoreElements()) {
                String key = (String)e.nextElement();
                Object requestVal = requestArguments.get(key);
                if(requestVal instanceof String) {
                    String value = (String)requestVal;
                    b.append(key);
                    b.append("=");
                    b.append(value);
                    if(e.hasMoreElements()) {
                        b.append("&");
                    }
                    continue;
                }
                String[] val = (String[])requestVal;
                int vlen = val.length;
                for(int iter = 0 ; iter < vlen - 1; iter++) {
                    b.append(key);
                    b.append("=");
                    b.append(val[iter]);
                    b.append("&");
                }
                b.append(key);
                b.append("=");
                b.append(val[vlen - 1]);
                if(e.hasMoreElements()) {
                    b.append("&");
                }
            }
            return b.toString();
        }
        return url;
    }

    /**
     * Invoked when send body is true, by default sends the request arguments based
     * on "POST" conventions
     *
     * @param os output stream of the body
     */
    protected void buildRequestBody(OutputStream os) throws IOException {
        if(post && requestArguments != null) {
            StringBuilder val = new StringBuilder();
            Enumeration e = requestArguments.keys();
            while(e.hasMoreElements()) {
                String key = (String)e.nextElement();
                Object requestVal = requestArguments.get(key);
                if(requestVal instanceof String) {
                    String value = (String)requestVal;
                    val.append(key);
                    val.append("=");
                    val.append(value);
                    if(e.hasMoreElements()) {
                        val.append("&");
                    }
                    continue;
                }
                String[] valArray = (String[])requestVal;
                int vlen = valArray.length;
                for(int iter = 0 ; iter < vlen - 1; iter++) {
                    val.append(key);
                    val.append("=");
                    val.append(valArray[iter]);
                    val.append("&");
                }
                val.append(key);
                val.append("=");
                val.append(valArray[vlen - 1]);
                if(e.hasMoreElements()) {
                    val.append("&");
                }
            }
            if(shouldWriteUTFAsGetBytes()) {
                os.write(val.toString().getBytes("UTF-8"));
            } else {
                OutputStreamWriter w = new OutputStreamWriter(os, "UTF-8");
                w.write(val.toString());
            }
        }
    }
    
    /**
     * Returns whether when writing a post body the platform expects something in the form of 
     * string.getBytes("UTF-8") or new OutputStreamWriter(os, "UTF-8"). 
     */
    protected boolean shouldWriteUTFAsGetBytes() {
        return Util.getImplementation(this).shouldWriteUTFAsGetBytes();
    }
    
    /**
     * Kills this request if possible
     */
    public void kill() {
        killed = true;
        //if the connection is in the midle of a reading, stop it to release the 
        //resources
        if(input != null && input instanceof BufferedInputStream) {
            try {
                ((BufferedInputStream)input).close();
            } catch (IOException ex) {
                Logger.getLogger(ConnectionRequest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //NetworkManager.getInstance().kill9(this);
    }

    /**
     * Returns true if the request is paused or killed, developers should call this
     * method periodically to test whether they should quit the current IO operation immediately
     *
     * @return true if the request is paused or killed
     */
    protected boolean shouldStop() {
        return isPaused() || isKilled();
    }

    /**
     * Return true from this method if this connection can be paused and resumed later on.
     * A pausable network operation receives a "pause" invocation and is expected to stop
     * network operations as soon as possible. It will later on receive a resume() call and
     * optionally start downloading again.
     *
     * @return false by default.
     */
    protected boolean isPausable() {
        return false;
    }

    /**
     * Invoked to pause this opeation, this method will only be invoked if isPausable() returns true
     * (its false by default). After this method is invoked current network operations should
     * be stoped as soon as possible for this class.
     *
     * @return This method can return false to indicate that there is no need to resume this
     * method since the operation has already been completed or made redundant
     */
    public boolean pause() {
        paused = true;
        return true;
    }

    /**
     * Called when a previously paused operation now has the networking time to resume.
     * Assuming this method returns true, the network request will be resent to the server
     * and the operation can resume.
     *
     * @return This method can return false to indicate that there is no need to resume this
     * method since the operation has already been completed or made redundant
     */
    public boolean resume() {
        paused = false;
        return true;
    }

    /**
     * Returns true for a post operation and false for a get operation
     *
     * @return the post
     */
    public boolean isPost() {
        return post;
    }

    /**
     * Set to true for a post operation and false for a get operation, this will implicitly 
     * set the method to post/get respectively (which you can change back by setting the method).
     * The main importance of this method is how arguments are added to the request (within the 
     * body or in the URL) and so it is important to invoke this method before any argument was 
     * added.
     *
     * @throws IllegalStateException if invoked after an addArgument call
     */
    public void setPost(boolean post) {
        if(this.post != post && requestArguments != null && requestArguments.size() > 0) {
            throw new IllegalStateException("Request method (post/get) can't be modified once arguments have been assigned to the request");
        }
        this.post = post;
        if(this.post) {
            setWriteRequest(true);
        }
    }

    /**
     * Add an argument to the request response
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    private void addArg(String key, Object value) {
        if(requestArguments == null) {
            requestArguments = new Hashtable();
        }
        if(value == null || key == null){
            return;
        }
        if(post) {
            // this needs to be implicit for a post request with arguments
            setWriteRequest(true);
        }
        requestArguments.put(key, value);
    }

    /**
     * Add an argument to the request response
     *
     * @param key the key of the argument
     * @param value the value for the argument
     * @deprecated use the version that accepts a string instead
     */
    public void addArgument(String key, byte[] value) {
        key = key.intern();
        if(post) {
            addArg(Util.encodeBody(key), Util.encodeBody(value));
        } else {
            addArg(Util.encodeUrl(key), Util.encodeUrl(value));
        }
    }

    /**
     * Removes the given argument from the request 
     * 
     * @param key the key of the argument no longer used
     */
    public void removeArgument(String key) {
        if(requestArguments != null) {
            requestArguments.remove(key);
        }
    }

    /**
     * Removes all arguments
     */
    public void removeAllArguments() {
        requestArguments = null;
    }
    
    /**
     * Add an argument to the request response without encoding it, this is useful for
     * arguments which are already encoded
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArgumentNoEncoding(String key, String value) {
        addArg(key, value);
    }

    /**
     * Add an argument to the request response as an array of elements, this will
     * trigger multiple request entries with the same key, notice that this doesn't implicitly
     * encode the value
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArgumentNoEncoding(String key, String[] value) {
        if(value == null || value.length == 0) {
            return;
        }
        if(value.length == 1) {
            addArgumentNoEncoding(key, value[0]);
            return;
        }
        // copying the array to prevent mutation
        String[] v = new String[value.length];
        System.arraycopy(value, 0, v, 0, value.length);
        addArg(key, v);
    }
    
    /**
     * Add an argument to the request response as an array of elements, this will
     * trigger multiple request entries with the same key, notice that this doesn't implicitly
     * encode the value
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArgumentNoEncodingArray(String key, String... value) {
        addArgumentNoEncoding(key, (String[])value);
    }

    /**
     * Add an argument to the request response
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArgument(String key, String value) {
        if(post) {
            addArg(Util.encodeBody(key), Util.encodeBody(value));
        } else {
            addArg(Util.encodeUrl(key), Util.encodeUrl(value));
        }
    }

    /**
     * Add an argument to the request response as an array of elements, this will
     * trigger multiple request entries with the same key
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArgumentArray(String key, String... value) {
        addArgument(key, value);
    }
    
    /**
     * Add an argument to the request response as an array of elements, this will
     * trigger multiple request entries with the same key
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArgument(String key, String[] value) {
        // copying the array to prevent mutation
        String[] v = new String[value.length];
        if(post) {
            int vlen = value.length;
            for(int iter = 0 ; iter < vlen ; iter++) {
                v[iter] = Util.encodeBody(value[iter]);
            }
            addArg(Util.encodeBody(key), v);
        } else {
            int vlen = value.length;
            for(int iter = 0 ; iter < vlen ; iter++) {
                v[iter] = Util.encodeUrl(value[iter]);
            }
            addArg(Util.encodeUrl(key), v);
        }
    }

    /**
     * Add an argument to the request response as an array of elements, this will
     * trigger multiple request entries with the same key
     *
     * @param key the key of the argument
     * @param value the value for the argument
     */
    public void addArguments(String key, String... value) {
        if(value.length == 1) {
            addArgument(key, value[0]);
        } else {
            addArgument(key, (String[])value);
        }
    }

    /**
     * @return the contentType
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @param contentType the contentType to set
     */
    public void setContentType(String contentType) {
        contentTypeSetExplicitly = true;
        this.contentType = contentType;
    }

    /**
     * @return the writeRequest
     */
    public boolean isWriteRequest() {
        return writeRequest;
    }

    /**
     * @param writeRequest the writeRequest to set
     */
    public void setWriteRequest(boolean writeRequest) {
        this.writeRequest = writeRequest;
    }

    /**
     * @return the readRequest
     */
    public boolean isReadRequest() {
        return readRequest;
    }

    /**
     * @param readRequest the readRequest to set
     */
    public void setReadRequest(boolean readRequest) {
        this.readRequest = readRequest;
    }

    /**
     * @return the paused
     */
    protected boolean isPaused() {
        return paused;
    }

    /**
     * @param paused the paused to set
     */
    protected void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * @return the killed
     */
    protected boolean isKilled() {
        return killed;
    }

    /**
     * @param killed the killed to set
     */
    protected void setKilled(boolean killed) {
        this.killed = killed;
    }

    /**
     * The priority of this connection based on the constants in this class
     *
     * @return the priority
     */
    public byte getPriority() {
        return priority;
    }

    /**
     * The priority of this connection based on the constants in this class
     * 
     * @param priority the priority to set
     */
    public void setPriority(byte priority) {
        this.priority = priority;
    }

    /**
     * @return the userAgent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * @param userAgent the userAgent to set
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * @return the defaultUserAgent
     */
    public static String getDefaultUserAgent() {
        return defaultUserAgent;
    }

    /**
     * @param aDefaultUserAgent the defaultUserAgent to set
     */
    public static void setDefaultUserAgent(String aDefaultUserAgent) {
        defaultUserAgent = aDefaultUserAgent;
    }

    /**
     * Enables/Disables automatic redirects globally and returns the 302 error code, <strong>IMPORTANT</strong>
     * this feature doesn't work on all platforms and currently doesn't work on iOS which always implicitly redirects
     * @return the followRedirects
     */
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     * Enables/Disables automatic redirects globally and returns the 302 error code, <strong>IMPORTANT</strong>
     * this feature doesn't work on all platforms and currently doesn't work on iOS which always implicitly redirects
     * @param followRedirects the followRedirects to set
     */
    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    /**
     * Indicates the timeout for this connection request 
     *
     * @return the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Indicates the timeout for this connection request 
     * 
     * @param timeout the timeout to set
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * This method prevents a manual timeout from occurring when invoked at a frequency faster
     * than the timeout.
     */
    void updateActivity() {
        timeSinceLastUpdate = System.currentTimeMillis();
    }

    /**
     * Returns the time since the last activity update
     */
    int getTimeSinceLastActivity() {
        return (int)(System.currentTimeMillis() - timeSinceLastUpdate);
    }

    /**
     * Returns the content length header value
     *
     * @return the content length
     */
    public int getContentLength() {
        return contentLength;
    }

    /**
     * {@inheritDoc}
     */
    public void ioStreamUpdate(Object source, int bytes) {
        if(!isKilled()) {
            //NetworkManager.getInstance().fireProgressEvent(this, progress, getContentLength(), bytes);
        }
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param url the url to set
     */
    public void setUrl(String url) {
        if(url.indexOf(' ') > -1) {
            url = StringUtil.replaceAll(url, " ", "%20");
        }
        url = url.intern();
        this.url = url;
    }

    private List<ActionListener> actionListeners;
    
    /**
     * Adds a listener that would be notified on the CodenameOne thread of a response from the server.
     * This event is specific to the connection request type and its firing will change based on
     * how the connection request is read/processed
     *
     * @param a listener
     */
    public void addResponseListener(ActionListener<NetworkEvent> a) {
        if(actionListeners == null) {
            actionListeners = new ArrayList<ActionListener>();
            //actionListeners.setBlocking(false);
        }
        actionListeners.add(a);
    }

    /**
     * Removes the given listener
     *
     * @param a listener
     */
    public void removeResponseListener(ActionListener<NetworkEvent> a) {
        if(actionListeners == null) {
            return;
        }
        actionListeners.remove(a);
        //if(actionListeners.getListenerCollection()== null || actionListeners.getListenerCollection().size() == 0) {
        //    actionListeners = null;
        //}
    }

    /**
     * Adds a listener that would be notified on the CodenameOne thread of a response code that
     * is not a 200 (OK) or 301/2 (redirect) response code.
     *
     * @param a listener
     */
    public void addResponseCodeListener(ActionListener<NetworkEvent> a) {
        if(responseCodeListeners == null) {
            responseCodeListeners = new ArrayList<ActionListener>();
            //responseCodeListeners.setBlocking(false);
        }
        responseCodeListeners.add(a);
    }

    /**
     * Removes the given listener
     *
     * @param a listener
     */
    public void removeResponseCodeListener(ActionListener<NetworkEvent> a) {
        if(responseCodeListeners == null) {
            return;
        }
        responseCodeListeners.remove(a);
        //if(responseCodeListeners.getListenerCollection()== null || responseCodeListeners.getListenerCollection().size() == 0) {
        //    responseCodeListeners = null;
        //}
    }

    /**
     * Returns true if someone is listening to action response events, this is useful
     * so we can decide whether to bother collecting data for an event in some cases
     * since building the event object might be memory/CPU intensive.
     * 
     * @return true or false
     */
    protected boolean hasResponseListeners() {
        return actionListeners != null;
    }

    /**
     * Fires the response event to the listeners on this connection
     *
     * @param ev the event to fire
     */
    protected void fireResponseListener(ActionEvent ev) {
        if(actionListeners != null) {
            fireActionEvent(actionListeners, ev);
        }
    }

    /**
     * Indicates whether this connection request supports duplicate entries in the request queue
     *
     * @return the duplicateSupported value
     */
    public boolean isDuplicateSupported() {
        return duplicateSupported;
    }

    /**
     * Indicates whether this connection request supports duplicate entries in the request queue
     * 
     * @param duplicateSupported the duplicateSupported to set
     */
    public void setDuplicateSupported(boolean duplicateSupported) {
        this.duplicateSupported = duplicateSupported;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        if(url != null) {
            int i = url.hashCode();
            if(requestArguments != null) {
                i = i ^ requestArguments.hashCode();
            }
            return i;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if(o != null && o.getClass() == getClass()) {
            ConnectionRequest r = (ConnectionRequest)o;

            // interned string comparison
            if(r.url == url) {
                if(requestArguments != null) {
                    if(r.requestArguments != null && requestArguments.size() == r.requestArguments.size()) {
                        Enumeration e = requestArguments.keys();
                        while(e.hasMoreElements()) {
                            Object key = e.nextElement();
                            Object value = requestArguments.get(key);
                            Object otherValue = r.requestArguments.get(key);
                            if(otherValue == null || !value.equals(otherValue)) {
                                return false;
                            }
                        }
                        return r.killed == killed;
                    }
                } else {
                    if(r.requestArguments == null) {
                        return r.killed == killed;
                    }
                }
            }
        }
        return false;
    }

    void validateImpl() {
        if(url == null) {
            throw new IllegalStateException("URL is null");
        }
        if(url.length() == 0) {
            throw new IllegalStateException("URL is empty");
        }
        validate();
    }

    /**
     * Validates that the request has the required information before being added to the queue
     * e.g. checks if the URL is null. This method should throw an IllegalStateException for
     * a case where one of the values required for this connection request is missing.
     * This method can be overriden by subclasses to add additional tests. It is usefull
     * to do tests here since the exception will be thrown immediately when invoking addToQueue
     * which is more intuitive to debug than the alternative.
     */
    protected void validate() {
        if(!url.toLowerCase().startsWith("http")) {
            throw new IllegalStateException("Only HTTP urls are supported!");
        }
    }

    

    /**
     * Indicates the number of times to silently retry a connection that failed
     * before prompting
     *
     * @return the silentRetryCount
     */
    public int getSilentRetryCount() {
        return silentRetryCount;
    }

    /**
     * Indicates the number of times to silently retry a connection that failed
     * before prompting
     * @param silentRetryCount the silentRetryCount to set
     */
    public void setSilentRetryCount(int silentRetryCount) {
        this.silentRetryCount = silentRetryCount;
    }

    /**
     * Indicates that we are uninterested in error handling
     * @return the failSilently
     */
    public boolean isFailSilently() {
        return failSilently;
    }

    /**
     * Indicates that we are uninterested in error handling
     * @param failSilently the failSilently to set
     */
    public void setFailSilently(boolean failSilently) {
        this.failSilently = failSilently;
    }
    
    /**
     * Indicates whether the native Cookie stores should be used
     * @param b true to enable native cookie stores when applicable
     */
    public static void setUseNativeCookieStore(boolean b) {
        //Util.getImplementation(this).setUseNativeCookieStore(b);
    }

    /**
     * When set to true the read response code will happen even for error codes such as 400 and 500
     * @return the readResponseForErrors
     */
    public boolean isReadResponseForErrors() {
        return readResponseForErrors;
    }

    /**
     * When set to true the read response code will happen even for error codes such as 400 and 500
     * @param readResponseForErrors the readResponseForErrors to set
     */
    public void setReadResponseForErrors(boolean readResponseForErrors) {
        this.readResponseForErrors = readResponseForErrors;
    }
    
    /**
     * Returns the content type from the response headers
     * @return the content type
     */
    public String getResponseContentType() {
        return responseContentType;
    }
    
    /**
     * Returns true if this request is been redirected to a different url
     * @return true if redirecting
     */ 
    public boolean isRedirecting(){
        return redirecting;
    }

    /**
     * When set to a none null string saves the response to file system under
     * this file name
     * @return the destinationFile
     */
    public String getDestinationFile() {
        return destinationFile;
    }

    /**
     * When set to a none null string saves the response to file system under
     * this file name
     * @param destinationFile the destinationFile to set
     */
    public void setDestinationFile(String destinationFile) {
        this.destinationFile = destinationFile;
    }

    /**
     * When set to a none null string saves the response to storage under
     * this file name
     * @return the destinationStorage
     */
    public String getDestinationStorage() {
        return destinationStorage;
    }

    /**
     * When set to a none null string saves the response to storage under
     * this file name
     * @param destinationStorage the destinationStorage to set
     */
    public void setDestinationStorage(String destinationStorage) {
        this.destinationStorage = destinationStorage;
    }

    /**
     * @return the cookiesEnabled
     */
    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    /**
     * @param cookiesEnabled the cookiesEnabled to set
     */
    public void setCookiesEnabled(boolean cookiesEnabled) {
        this.cookiesEnabled = cookiesEnabled;
        if(!cookiesEnabled) {
            setUseNativeCookieStore(false);
        }
    }
    
     /**
     * This method is used to enable streaming of a HTTP request body without 
     * internal buffering, when the content length is not known in advance. 
     * In this mode, chunked transfer encoding is used to send the request body. 
     * Note, not all HTTP servers support this mode.
     * This mode is supported on Android and the Desktop ports.
     * 
     * @param chunklen The number of bytes to write in each chunk. If chunklen 
     * is zero a default value will be used.
     */ 
    public void setChunkedStreamingMode(int chunklen){    
        this.chunkedStreamingLen = chunklen;
    }
   

    /**
     * Utility method that returns a JSON structure or throws an IOException in case of a failure.
     * This method blocks the EDT legally and can be used synchronously. Notice that this method assumes
     * all JSON data is UTF-8
     * @param url the URL hosing the JSON
     * @return map data
     * @throws IOException in case of an error
     */
    public static Map<String, Object> fetchJSON(String url) throws IOException {
        ConnectionRequest cr = new ConnectionRequest();
        cr.setFailSilently(true);
        cr.setPost(false);
        cr.setUrl(url);
        cr.addToQueueAndWait();
        if(cr.getResponseData() == null) {
            if(cr.failureException != null) {
                throw new IOException(cr.failureException.toString());
            } else {
                throw new IOException("Server returned error code: " + cr.failureErrorCode);
            }
        }
        JSONParser jp = new JSONParser();
        Map<String, Object> result = jp.parseJSON(new InputStreamReader(new ByteArrayInputStream(cr.getResponseData()), "UTF-8"));
        return result;
    }
    
    private List<ActionListener> errorListeners;
    
    
    
    private boolean handleException(ConnectionRequest r, Exception o) {
        if(errorListeners != null) {
            ActionEvent ev = new NetworkEvent(r, o);
            fireActionEvent(errorListeners, ev);
            //errorListeners.fireActionEvent(ev);
            return ev.isConsumed();
        }
        return false;
    }
    
    
    public void addToQueueAndWait() {
        boolean[] complete = new boolean[1];
        final Runnable run = new Runnable() {
            public void run() {
                    
                    try {
                        performOperation(); 
                    } catch(IOException e) {
                        if(!isFailSilently()) {
                            if(!handleException(ConnectionRequest.this, e)) {
                                handleIOException(e);
                            }
                        } else {
                            // for the record
                            e.printStackTrace();
                        }
                    } catch(RuntimeException er) {
                        if(!isFailSilently()) {
                            if(!handleException(ConnectionRequest.this, er)) {
                                handleRuntimeException(er);
                            }
                        } else {
                            // for the record
                            er.printStackTrace();
                        }
                    
                    } finally {
                        synchronized (complete) {
                            complete[0] = true;
                            complete.notifyAll();
                        }
                    }
                }
        };
        
        if (networkExecutor == null) {
           run.run();
        } else {
            
            networkExecutor.execute(run);
            synchronized(complete) {
                while (!complete[0]) {
                    try {
                        complete.wait(100);
                    } catch (Exception ex){}
                }
            }
        }
    }
}