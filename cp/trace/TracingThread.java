/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import com.sun.jdi.*;
import com.sun.jdi.request.*;
import com.sun.jdi.event.*;

import java.util.*;
import java.io.*;
import javax.json.*;

/* 
 * Original author: Robert Field, see
 * http://www.docjar.com/src/api/com/sun/tools/example/trace/EventThread.java
 *
 * This version: David Pritchard (http://dave-pritchard.net)
 */

public class TracingThread extends Thread {

    private final VirtualMachine vm;   // Running VM
    private String[] no_breakpoint_requests = {"java.*", "javax.*", "sun.*", "com.sun.*"};

    private boolean connected = true;  // Connected to VM
    private boolean vmDied = true;     // VMDeath occurred
    
    EventRequestManager mgr; 
    
    JDI2JSON jdi2json;

    private int MAX_STEPS = 256;
    private int steps = 0;
    
    TracingThread(VirtualMachine vm) {
        super("event-handler");
        this.vm = vm;
        mgr = vm.eventRequestManager();
        jdi2json = new JDI2JSON(vm,
                                vm.process().getInputStream(),
                                vm.process().getErrorStream());
        setEventRequests();
    }
    
    void setEventRequests() {
        ExceptionRequest excReq = mgr.createExceptionRequest(null, true, true);
        excReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        for (String clob : no_breakpoint_requests)
            excReq.addClassExclusionFilter(clob);
        excReq.enable();

        MethodEntryRequest menr = mgr.createMethodEntryRequest();
        for (String clob : no_breakpoint_requests)
            menr.addClassExclusionFilter(clob);
        menr.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        menr.enable();

        MethodExitRequest mexr = mgr.createMethodExitRequest();
        for (String clob : no_breakpoint_requests)
            mexr.addClassExclusionFilter(clob);
        mexr.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        mexr.enable();

        ThreadDeathRequest tdr = mgr.createThreadDeathRequest();
        tdr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        tdr.enable();

        ClassPrepareRequest cpr = mgr.createClassPrepareRequest();
        for (String clob : no_breakpoint_requests)
            cpr.addClassExclusionFilter(clob);
        cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        cpr.enable();
    }

    JsonArrayBuilder output = Json.createArrayBuilder();
    
    @Override
    public void run() {
        StepRequest request = null;
        final EventQueue queue = vm.eventQueue();
        while (connected) {
            try {
                final EventSet eventSet = queue.remove();
                for (Event ev : new Iterable<Event>(){public Iterator<Event> iterator(){return eventSet.eventIterator();}}) {
                    handleEvent(ev);
                    if (request != null && request.isEnabled()) {
                        request.disable();
                    }
                    if (request != null) {
                        mgr.deleteEventRequest(request);
                        request = null;
                    }
                    if (ev instanceof LocatableEvent && 
                        jdi2json.reportEventsAtLocation(((LocatableEvent)ev).location())) {
                        request = mgr.
                            createStepRequest(((LocatableEvent)ev).thread(),
                                              StepRequest.STEP_MIN,
                                              StepRequest.STEP_INTO);
                        request.addCountFilter(1);  // next step only
                        request.enable();
                    }
                }
                eventSet.resume();
            } catch (InterruptedException exc) {
                // Ignore
            } catch (VMDisconnectedException discExc) {
                handleDisconnectedException();
                break;
            }
        }
        if (steps == 0) {
            // not the most elegant way to detect this, but the approaches
            // I tried led to classloaders running in the wrong places
            System.out.println(JDI2JSON.error("Did not find: public static void main(String[])"));
        }
        else {
            System.out.println(output.build().toString());
        }
    }

    ThreadReference theThread = null;
        
    private void handleEvent(Event event) {
        /*if (event instanceof ExceptionEvent) {
            exceptionEvent((ExceptionEvent)event);
            } else*/ 
        if (event instanceof ClassPrepareEvent) {
            classPrepareEvent((ClassPrepareEvent)event);
        } else if (event instanceof VMDeathEvent) {
            vmDeathEvent((VMDeathEvent)event);
        } else if (event instanceof VMDisconnectEvent) {
            vmDisconnectEvent((VMDisconnectEvent)event);
        } 
        
        if (event instanceof LocatableEvent) {
            if (theThread == null)
                theThread = ((LocatableEvent)event).thread();
            else {
                if (theThread != ((LocatableEvent)event).thread())
                    throw new RuntimeException("Assumes one thread!");
            }
            Location loc = ((LocatableEvent)event).location();
            if (steps < MAX_STEPS && jdi2json.reportEventsAtLocation(loc)) {
		try {
		    for (JsonObject ep : jdi2json.convertExecutionPoint(event, loc, theThread)) {
			output.add(ep);
			steps++;	  
			if (steps == MAX_STEPS) {
                            output.add(Json.createObjectBuilder()
                                       .add("exception_msg", "<ran for maximum execution time limit>")
                                       .add("event", "instruction_limit_reached"));
			    vm.exit(0);
			}
		    }
		} catch (RuntimeException e) {
		    System.out.println("Error " + e.toString());
		    e.printStackTrace();
		}
            }
        }
    }
    
    /***
     * A VMDisconnectedException has happened while dealing with
     * another event. We need to flush the event queue, dealing only
     * with exit events (VMDeath, VMDisconnect) so that we terminate
     * correctly.
     */
    synchronized void handleDisconnectedException() {
        EventQueue queue = vm.eventQueue();
        while (connected) {
            try {
                EventSet eventSet = queue.remove();
                EventIterator iter = eventSet.eventIterator();
                while (iter.hasNext()) {
                    Event event = iter.nextEvent();
                    if (event instanceof VMDeathEvent) {
                        vmDeathEvent((VMDeathEvent)event);
                    } else if (event instanceof VMDisconnectEvent) {
                        vmDisconnectEvent((VMDisconnectEvent)event);
                    }
                }
                eventSet.resume(); // Resume the VM
            } catch (InterruptedException exc) {
                // ignore
            }
        }
    }

    private void classPrepareEvent(ClassPrepareEvent event)  {
        //System.out.println("CPE!");
        ReferenceType rt = event.referenceType();
	//	System.out.println(rt.toString());
        jdi2json.staticListable.add(rt);
        try {
            for (Location loc : rt.allLineLocations()) {
                BreakpointRequest br = mgr.createBreakpointRequest(loc);
                br.enable();
            }
        }
        catch (AbsentInformationException e) {
            System.out.println("AIE!");
        }
    }

    public void vmDeathEvent(VMDeathEvent event) {
        vmDied = true;
    }

    public void vmDisconnectEvent(VMDisconnectEvent event) {
        connected = false;
    }
    
}
