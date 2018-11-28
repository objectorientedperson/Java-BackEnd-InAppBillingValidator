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
package com.smartral.inappbilling.utils.ui.events;


/**
 * Event object delivered when an {@link ActionListener} callback is invoked
 * 
 * @author Chen Fishbein
 */
public class ActionEvent {
	
    /**
     * The event type, as declared when the event is created.
     * 
     * @author Ddyer
     *
     */
    public enum Type {
            /**
             * Unspecified command type, this occurs when one of the old undifferentiated constructors was invoked
             */
            Other,
            
            /**
             * Triggered by a command
             */
            Command,				
            
            /**
             * Pointer event that doesn't necessarily fall into one of the other pointer event values
             */
            Pointer, 
            
            /**
             * Pointer event
             */
            PointerPressed, 
            
            /**
             * Pointer event
             */
            PointerReleased, 
            
            
            /**
             * Pointer event
             */
            PointerDrag, 
            
            
            /**
             * Pointer swipe event currently fired by {@link com.smartral.inappbilling.utils.ui.SwipeableContainer#addSwipeOpenListener(com.smartral.inappbilling.utils.ui.events.ActionListener)}
             */
            Swipe,	
            
            /**
             * Fired by key events
             */
            KeyPress, 
            
            
            /**
             * Fired by key events
             */
            KeyRelease,	
            
            /**
             * Network event fired in case of a network error
             */
            Exception, 
            
            /**
             * Network event fired in case of a network response code event
             */
            Response,
            
            /**
             * Network event fired in case of progress update
             */
            Progress,
            
            /**
             * Network event fired in case of a network response containing data
             */
            Data, 	
            
            /**
             * Event from {@link com.smartral.inappbilling.utils.ui.Calendar}
             */
            Calendar,			
            
            /**
             * Fired on a {@link com.smartral.inappbilling.utils.ui.TextArea} action event
             */
            Edit,
            
            /**
             * Fired on a {@link com.smartral.inappbilling.utils.ui.TextField#setDoneListener(com.smartral.inappbilling.utils.ui.events.ActionListener)} action event
             */
            Done,			
            
            /**
             * Fired by the {@link com.smartral.inappbilling.utils.javascript.JavascriptContext} 
             */
            JavaScript,
            
            /**
             * Logging event to used for log/filesystem decoupling
             */
            Log,
            
            /**
             * Fired when the theme changes
             */
            Theme, 
            
            /**
             * Fired when a {@link com.smartral.inappbilling.utils.ui.Form} is shown
             */
            Show, 
            
            
            /**
             * Fired when a {@link com.smartral.inappbilling.utils.ui.Form#sizeChanged(int, int)} occurs 
             */
            SizeChange, 
            
            /**
             * Fired when a {@link com.smartral.inappbilling.utils.ui.Form} is rotated 
             */
            OrientationChange	
            } ;
    private Type trigger;
    
    /**
     * Returns the type of the given event allowing us to have more generic event handling code and useful
     * for debugging
     * @return the Type enum
     */
    public Type getEventType() { return(trigger); }
	
    private boolean consumed;
    
    private Object source;
    private Object sourceComponent;
    
    private int keyEvent = -1;
    private int y = -1;
    private boolean longEvent = false;
    
    /**
     * Creates a new instance of ActionEvent.  This is unused locally, but provided so existing customer code
     * with still work.
     * @param source element for the action event
     */
    public ActionEvent(Object source) {
        this.source = source;
        this.trigger = Type.Other;
    }
    
    /**
     * Creates a new instance of ActionEvent
     * @param source element for the action event
     * @param type the {@link Type } of the event
     */
    public ActionEvent(Object source,Type type) {
        this.source = source;
        this.trigger = type;
    }

    /**
     * Creates a new instance of ActionEvent as a pointer event
     *
     * @param source element for the pointer event
     * @param type the {@link Type } of the event
     * @param x (or sometimes width) associated with the event
     * @param y (or sometimes height)associated with the event
     */
    public ActionEvent(Object source, Type type, int x, int y) {
        this.source = source;
        this.keyEvent = x;
        this.y = y;
        this.trigger = type;
    }
    

    
    
    /**
     * Creates a new instance of ActionEvent.  The key event is really just
     * a numeric code, not indicative of a key press
     * @param source element for the action event
     * @param type the {@link Type } of the event
     * @param keyEvent the key that triggered the event
     */
    public ActionEvent(Object source, Type type , int keyEvent) {
        this.source = source;
        this.keyEvent = keyEvent;
        this.trigger = type;
    }
    
    
    /**
     * Creates a new instance of ActionEvent
     * @param source element for the action event
     * @param keyEvent the key that triggered the event
     */
    public ActionEvent(Object source, int keyEvent) {
        this.source = source;
        this.keyEvent = keyEvent;
        this.trigger = Type.KeyRelease;
    }

    /**
     * Creates a new instance of ActionEvent
     * @param source element for the action event
     * @param keyEvent the key that triggered the event
     * @param longClick true if the event is triggered from long pressed
     */
    public ActionEvent(Object source, int keyEvent, boolean longClick) {
        this.source = source;
        this.keyEvent = keyEvent;
        this.longEvent = longClick;
        this.trigger = Type.KeyPress;
    }
    
    /**
     * Creates a new instance of ActionEvent as a pointer event
     *
     * @param source element for the pointer event
     * @param x the x position of the pointer event
     * @param y the y position of the pointer event
     * @param longPointer true if the event is triggered from long pressed
     */
    public ActionEvent(Object source, int x, int y, boolean longPointer) {
        this.source = source;
        this.keyEvent = x;
        this.y = y;
        this.longEvent = longPointer;
        this.trigger = Type.PointerReleased;
    }
    
    /**
     * Creates a new instance of ActionEvent as a generic pointer event.  
     *
     * @param source element for the pointer event
     * @param x the x position of the pointer event
     * @param y the y position of the pointer event
     */
    public ActionEvent(Object source, int x, int y) {
        this.source = source;
        this.keyEvent = x;
        this.y = y;
        this.trigger = Type.Pointer;
    }

    
    
    /**
     * The element that triggered the action event, useful for decoupling event
     * handling code
     * @return the element that triggered the action event
     */
    public Object getSource(){
        return source;
    }

    /**
     * If this event was triggered by a key press this method will return the 
     * appropriate keycode
     * @return the key that triggered the event
     */
    public int getKeyEvent() {
        return keyEvent;
    }

    
    
   
    
    /**
     * Consume the event indicating that it was handled thus preventing other action
     * listeners from handling/receiving the event
     */
    public void consume() {
        consumed = true;
    }
    
    /**
     * Returns true if the event was consumed thus indicating that it was handled.
     * This prevents other action listeners from handling/receiving the event
     * 
     * @return true if the event was consumed
     */
    public boolean isConsumed() {
        return consumed;
    }

    /**
     * The X position if this is a pointer event otherwise undefined
     *
     * @return x position
     */
    public int getX() {
        return keyEvent;
    }


    /**
     * The Y position if this is a pointer event otherwise undefined
     *
     * @return y position
     */
    public int getY() {
        return y;
    }
    
    /**
     * Returns true for long click or long pointer event
     */ 
    public boolean isLongEvent(){
        return longEvent;
    }
    
    /**
     * Set in the case of a drop listener, returns the component being dragged
     * @return the component being dragged
     */
    
}
