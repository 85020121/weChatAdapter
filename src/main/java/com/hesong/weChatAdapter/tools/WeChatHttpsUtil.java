package com.hesong.weChatAdapter.tools;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

import com.hesong.weChatAdapter.model.AccessToken;

public class WeChatHttpsUtil {
    private static Logger log = Logger.getLogger(WeChatHttpsUtil.class);

    public final static String access_token_url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APPID&secret=APPSECRET";

    public static JSONObject httpsRequest(String requestUrl,
            String requestMethod, String outputStr) {
        JSONObject jsonObject = null;
        StringBuffer buffer = new StringBuffer();

        try {
            TrustManager[] tm = { new HttpsTrustManager() };
            SSLContext sslCtxt = SSLContext.getInstance("SSLv3");
            sslCtxt.init(null, tm, new java.security.SecureRandom());

            SSLSocketFactory ssf = sslCtxt.getSocketFactory();

            URL url = new URL(requestUrl);
            HttpsURLConnection httpsUrlConct = (HttpsURLConnection) url
                    .openConnection();
            httpsUrlConct.setSSLSocketFactory(ssf);

            httpsUrlConct.setDoInput(true);
            httpsUrlConct.setDoOutput(true);
            httpsUrlConct.setUseCaches(false);

            httpsUrlConct.setRequestMethod(requestMethod);

            if ("GET".equalsIgnoreCase(requestMethod)) {
                httpsUrlConct.connect();
                // TODO: use sleep to avoid ssl exception just like:
                // Received fatal alert: bad_record_mac
                // maybe find some other solutions
                Thread.sleep(1000);
            }

            if (outputStr != null) {
                OutputStream out = httpsUrlConct.getOutputStream();
                out.write(outputStr.getBytes("UTF-8"));
                out.close();
            }

            InputStream in = httpsUrlConct.getInputStream();
            InputStreamReader inputReader = new InputStreamReader(in, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputReader);

            String str = null;
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputReader.close();
            in.close();
            in = null;
            httpsUrlConct.disconnect();
            jsonObject = JSONObject.fromObject(buffer.toString());
        } catch (ConnectException ce) {
            log.error("WeChat server connection time out.");
            return getErrorMsg(9003, "WeChat server connection time out: "+ce.toString());
        } catch (Exception e) {
            log.error("HTTPS request error: " + e.toString());
            return getErrorMsg(9002, "HTTPS request error: " + e.toString());
        }

        return jsonObject;
    }

    public static JSONObject httpPostRequest(String requestUrl, String outputStr, int timeout) {
        StringBuffer buffer = new StringBuffer();
        InputStream in = null;
        try {
            URL url = new URL(requestUrl);
            URLConnection httpUrlConct = url.openConnection();

            httpUrlConct.setDoInput(true);
            httpUrlConct.setDoOutput(true);
            httpUrlConct.setUseCaches(false);
            httpUrlConct.setReadTimeout(timeout); // 0 to infini

            if (outputStr != null) {
                OutputStream out = httpUrlConct.getOutputStream();
                out.write(outputStr.getBytes("UTF-8"));
                out.close();
            }

            in = httpUrlConct.getInputStream();
            InputStreamReader inputReader = new InputStreamReader(in, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputReader);

            String str = null;
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputReader.close();
            
            return JSONObject.fromObject(buffer.toString());
            //jsonObject = JSONObject.fromObject(buffer.toString());
//            if(buffer.toString().equalsIgnoreCase("success")) {
//                return getErrorMsg(0, "OK");
//            } else {
//                return getErrorMsg(8001, "Request refused.");
//            }
        } catch(SocketTimeoutException se){
            log.error("Connection time out.");
            return getErrorMsg(9001, "HTTP connection timeout.");
        }catch (Exception e) {
            e.printStackTrace();
            log.error("Http request error: " + e.toString());
            return getErrorMsg(9009, "Http request error: " + e.toString());
        }
        
    }
    
    public static InputStream httpGetInputStream(String request, String contentType){
        try {
            URL url = new URL(request);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setUseCaches(false);
            
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Connection", "Keep-Alive");
            //connection.setRequestProperty("Content-type", contentType);
            return connection.getInputStream();
        } catch (Exception e) {
            log.error("httpGetInputStream exception: "+e.toString());
            return null;
        }
    }

    public static AccessToken getAccessToken(String appid, String appSecret) {
        AccessToken token = null;

        String requestUrl = access_token_url.replace("APPID", appid).replace(
                "APPSECRET", appSecret);
        JSONObject jo = httpsRequest(requestUrl, "GET", null);

        if (jo != null && jo.getString("access_token") != null) {
            try {
                token = new AccessToken(appid, appSecret,
                        jo.getString("access_token"), jo.getInt("expires_in"));
            } catch (Exception e) {
                log.error("Get token failed, errorcode:{"
                        + jo.getInt("errcode") + "} errormsg:{"
                        + jo.getString("errmsg") + "}");
                return null;
            }
        }

        return token;
    }
    
    public static JSONObject httpPostFile(String requestUrl, InputStream input) {
        StringBuffer buffer = new StringBuffer();
        InputStream in = null;
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection httpUrlConct = (HttpURLConnection)url.openConnection();

            httpUrlConct.setDoInput(true);
            httpUrlConct.setDoOutput(true);
            httpUrlConct.setUseCaches(false);
            httpUrlConct.setReadTimeout(0); // 0 to infini
            httpUrlConct.setRequestMethod("POST");
            httpUrlConct.setRequestProperty("Connection", "Keep-Alive");
            
            String BOUNDARY = "----------" + System.currentTimeMillis();
            httpUrlConct.setRequestProperty("Content-Type", "multipart/form-data; boundary="+ BOUNDARY);
            
            StringBuilder sb = new StringBuilder();
            sb.append("--"); // 必须多两道线
            sb.append(BOUNDARY);
            sb.append("\r\n");
            sb.append("Content-Disposition: form-data;name=\"file\";filename=\""
            + UUID.randomUUID().toString()+".jpg" + "\"\r\n");
            sb.append("Content-Type:application/octet-stream\r\n\r\n");

            byte[] head = sb.toString().getBytes("utf-8");
            
            OutputStream out = httpUrlConct.getOutputStream();
            out.write(head);
            int bytes = 0;
            byte[] bufferOut = new byte[1024];
            while ((bytes = input.read(bufferOut)) != -1) {
            out.write(bufferOut, 0, bytes);
            }
            input.close();
            byte[] foot = ("\r\n--" + BOUNDARY + "--\r\n").getBytes("utf-8");// 定义最后数据分隔线
            out.write(foot);
            log.info("Bytes: " + out.toString());
            out.flush();
            out.close();
            in = httpUrlConct.getInputStream();
            
            InputStreamReader inputReader = new InputStreamReader(in, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputReader);

            String str = null;
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputReader.close();
            
            return JSONObject.fromObject(buffer.toString());
        } catch(SocketTimeoutException se){
            log.error("Connection time out.");
            return getErrorMsg(9001, "HTTP connection timeout.");
        }catch (Exception e) {
            e.printStackTrace();
            log.error("Http request error: " + e.toString());
            return getErrorMsg(9009, "Http request error: " + e.toString());
        }
        
    }
    
    public static JSONObject getErrorMsg(int errcode, String errmsg) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("errcode", errcode);
        jsonObject.put("errmsg", errmsg);
        return jsonObject;
    }
}
