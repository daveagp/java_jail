/*****************************************************************************

traceprinter: a Java package to print traces of Java programs
David Pritchard (daveagp@gmail.com), created May 2013

The contents of this directory are released under the GNU Affero 
General Public License, versions 3 or later. See LICENSE or visit:
http://www.gnu.org/licenses/agpl.html

See README for documentation on this package.

This file was originally based on 
com.sun.tools.example.trace.Trace, written by Robert Field.

******************************************************************************/

package traceprinter;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;

public class JSONTrace {

    public static void main(String[] args) {
        new JSONTrace(args[0]);
    }

    // same as "java command": calls main
    // seems to fail silently if there is no main with the right signature 
    JSONTrace(String className) { 

        VirtualMachine vm = launchVM(className);	    
        vm.setDebugTraceMode(0);
        JSONTracingThread tt = new JSONTracingThread(vm, className);

        // used to check this, but it seems difficult to get it to work
        // correctly (c.f. printing in static initializer)
        // and it is not strictly needed to accomplish anything

        /*        java.util.List<ReferenceType> seek = vm.classesByName(className);
        if (seek.size() != 1) {
	    String msg = "Error: public class " + className + " not found";
	    System.out.println(JDI2JSON.error(msg));
	    throw new SorryDaveICantDoThat(msg);
	}
        ReferenceType rt = seek.get(0);
        java.util.List<Method> main = rt.methodsByName("main", "([Ljava/lang/String;)V");
        if (main.size() != 1 || !main.get(0).isStatic() || !main.get(0).isPublic()) {
	    String msg = "Error: class " + className + " needs public static void main(String[])";
	    System.out.println(JDI2JSON.error(msg));
	    throw new SorryDaveICantDoThat(msg);
            }*/

        tt.start();
        vm.resume();
        
    }

    VirtualMachine launchVM(String className) {
        LaunchingConnector connector = theCommandLineLaunchConnector();
        try {
            java.util.Map<String, Connector.Argument> args 
                = connector.defaultArguments();
            ((Connector.Argument)(args.get("main"))).setValue(className);
	    	    {
                        /*		for (java.util.Map.Entry<String, Connector.Argument> arg: args.entrySet()) {
		    System.out.println(arg.getKey());
		    System.out.println("["+arg.getValue().value()+"]");
		    System.out.println(arg.getValue().description());
		    System.out.println(arg.getValue().isValid(arg.getValue().value()));
                    }*/
		}
	    //System.out.println("About to call LaunchingConnector.launch...");
	    VirtualMachine result = connector.launch(args);
	    //System.out.println("...done");
            return result;
        } catch (java.io.IOException | VMStartException exc) {
	    //System.out.println("Hoey!");
            System.out.println("Failed in launchTarget: " + exc.getMessage());
            exc.printStackTrace();
        } catch (IllegalConnectorArgumentsException exc) {
	    //System.out.println("Hoey!");
            for (String S : exc.argumentNames()) {
                System.out.println(S);
            }
            System.out.println(exc);
        }
        return null; // when caught
    }

    LaunchingConnector theCommandLineLaunchConnector() {
        for (Connector connector : 
                 Bootstrap.virtualMachineManager().allConnectors()) 
            if (connector.name().equals("com.sun.jdi.CommandLineLaunch"))
                return (LaunchingConnector)connector;
        throw new Error("No launching connector");
    }


}
