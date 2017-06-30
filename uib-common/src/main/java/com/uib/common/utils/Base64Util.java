package com.uib.common.utils;

import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;

import sun.misc.*;

public class Base64Util {

	private static final Logger logger = Logger.getLogger("rootLogger");
	
	 /**
	  *  加密  
	  * @param str
	  * @return
	  */
    public static String getBase64(String str) {  
        byte[] b = null;  
        String s = null;  
        try {  
            b = str.getBytes("utf-8");  
        } catch (UnsupportedEncodingException e) {  
            e.printStackTrace();  
        }  
        if (b != null) {  
            s = new BASE64Encoder().encode(b);  
        }  
        return s;  
    }  
  
    /**
     *  解密  
     * @param s
     * @return
     */
    public static String getFromBase64(String s) {  
        byte[] b = null;  
        String result = null;  
        if (s != null) {  
            BASE64Decoder decoder = new BASE64Decoder();  
            try {  
                b = decoder.decodeBuffer(s);  
                result = new String(b, "utf-8");
            } catch (Exception e) {  
                e.printStackTrace();  
            }  
        }  
        return result;  
    }  
}
