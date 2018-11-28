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

import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Pluggable logging framework that allows a developer to log into storage
 * using the file connector API. It is highly recommended to use this 
 * class coupled with Netbeans preprocessing tags to reduce its overhead
 * completely in runtime.
 *
 * @author Shai Almog
 */
public class Log {
    private static boolean crashBound;
    /**
     * Constant indicating the logging level Debug is the default and the lowest level
     * followed by info, warning and error
     */
    public static final int DEBUG = 1;

    /**
     * Constant indicating the logging level Debug is the default and the lowest level
     * followed by info, warning and error
     */
    public static final int INFO = 2;

    /**
     * Constant indicating the logging level Debug is the default and the lowest level
     * followed by info, warning and error
     */
    public static final int WARNING = 3;

    /**
     * Constant indicating the logging level Debug is the default and the lowest level
     * followed by info, warning and error
     */
    public static final int ERROR = 4;
    
    
    private int level = DEBUG;
    private static Log instance = new Log();
    private long zeroTime = System.currentTimeMillis();
    private Writer output;
    private boolean fileWriteEnabled = false;
    private String fileURL = null;
    private boolean logDirty;
    
    /**
     * Indicates that log reporting to the cloud should be disabled
     */
    public static int REPORTING_NONE = 0; 

    /**
     * Indicates that log reporting to the cloud should occur regardless of whether an error occurred
     */
    public static int REPORTING_DEBUG = 1; 

    /**
     * Indicates that log reporting to the cloud should occur only if an error occurred
     */
    public static int REPORTING_PRODUCTION = 3; 
    
    private int reporting = REPORTING_NONE;

    private static boolean initialized;
    
    /**
     * Indicates the level of log reporting, this allows developers to send device logs to the cloud
     * thus tracking crashes or functionality in the device.
     * @param level one of REPORTING_NONE, REPORTING_DEBUG, REPORTING_PRODUCTION
     */
    public static void setReportingLevel(int level) {
        instance.reporting = level;
    }
    
    /**
     * Indicates the level of log reporting, this allows developers to send device logs to the cloud
     * thus tracking crashes or functionality in the device.
     * @return one of REPORTING_NONE, REPORTING_DEBUG, REPORTING_PRODUCTION
     */
    public static int getReportingLevel() {
        return instance.reporting;
    }
    
    /**
     * Prevent new Log() syntax. Use getInstance()
     */
    protected Log() {}
    
    
    
    /**
     * Installs a log subclass that can replace the logging destination/behavior
     * 
     * @param newInstance the new instance for the Log object
     */
    public static void install(Log newInstance) {
        instance = newInstance;
    }
    
    /**
     * Default println method invokes the print instance method, uses DEBUG level
     * 
     * @param text the text to print
     */
    public static void p(String text) {
        p(text, DEBUG);
    }
    
    /**
     * Default println method invokes the print instance method, uses given level
     * 
     * @param text the text to print
     * @param level one of DEBUG, INFO, WARNING, ERROR
     */
    public static void p(String text, int level) {
        instance.print(text, level);
    }

    /**
     * This method is a shorthand form for logThrowable
     *
     * @param t the exception
     */
    public static void e(Throwable t) {
        instance.logThrowable(t);
    }
    
    /**
     * Logs an exception to the log, by default print is called with the exception 
     * details, on supported devices the stack trace is also physically written to 
     * the log
     * @param t
     */
    protected void logThrowable(Throwable t) {
        print("Exception: " + t.getClass().getName() + " - " + t.getMessage(), ERROR);
        Thread thr = Thread.currentThread();
        
        t.printStackTrace();
        
    }

    /**
     * Default log implementation prints to the console and the file connector
     * if applicable. Also prepends the thread information and time before 
     * 
     * @param text the text to print
     * @param level one of DEBUG, INFO, WARNING, ERROR
     */
    protected void print(String text, int level) {
        if(!initialized) {
            initialized  = true;
            
        }
        if(this.level > level) {
            return;
        }
        logDirty = true;
        text = getThreadAndTimeStamp() + " - " + text;
        Logger.getLogger(getClass().getSimpleName()).log(Level.INFO,text);
    }
    
    

    /**
     * Returns a simple string containing a timestamp and thread name.
     * 
     * @return timestamp string for use in the log
     */
    protected String getThreadAndTimeStamp() {
        long time = System.currentTimeMillis() - zeroTime;
        long milli = time % 1000;
        time /= 1000;
        long sec = time % 60;
        time /= 60;
        long min = time % 60; 
        time /= 60;
        long hour = time % 60; 
        
        return "[" + Thread.currentThread().getName() + "] " + hour  + ":" + min + ":" + sec + "," + milli;
    }
    
    /**
     * Sets the logging level for printing log details, the lower the value 
     * the more verbose would the printouts be
     * 
     * @param level one of DEBUG, INFO, WARNING, ERROR
     */
    public static void setLevel(int level) {
        instance.level = level;
    }

    /**
     * Returns the logging level for printing log details, the lower the value 
     * the more verbose would the printouts be
     * 
     * @return one of DEBUG, INFO, WARNING, ERROR
     */
    public static int getLevel() {
        return instance.level;
    }
    
   
    /**
     * Returns the singleton instance of the log
     * 
     * @return the singleton instance of the log
     */
    public static Log getInstance() {
        return instance;
    }

    /**
     * Indicates whether GCF's file writing should be used to generate the log file
     *
     * @return the fileWriteEnabled
     */
    public boolean isFileWriteEnabled() {
        return fileWriteEnabled;
    }

    /**
     * Indicates whether GCF's file writing should be used to generate the log file
     * 
     * @param fileWriteEnabled the fileWriteEnabled to set
     */
    public void setFileWriteEnabled(boolean fileWriteEnabled) {
        this.fileWriteEnabled = fileWriteEnabled;
    }

    /**
     * Indicates the URL where the log file is saved
     *
     * @return the fileURL
     */
    public String getFileURL() {
        return fileURL;
    }

    /**
     * Indicates the URL where the log file is saved
     *
     * @param fileURL the fileURL to set
     */
    public void setFileURL(String fileURL) {
        this.fileURL = fileURL;
    }

    
   
    
    /**
     * Returns true if the user bound crash protection
     * @return true if crash protection is bound
     */
    public static boolean isCrashBound() {
        return crashBound;
    }
}
