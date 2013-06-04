import com.sun.jdi.*;
import com.sun.jdi.connect.*;

public class Trace {
    public static void main(String[] args) {
	try {
	    new Trace(args[0]);
	}
	catch (SorryDaveICantDoThat e) {}
    }

    class SorryDaveICantDoThat extends Exception {
	SorryDaveICantDoThat(String msg) {super(msg);}
    }

    // same as "java command": calls main
    // seems to fail silently if there is no main with the right signature 
    Trace(String className) throws SorryDaveICantDoThat {

        VirtualMachine vm = launchVM(className);	    
        vm.setDebugTraceMode(0);
        TracingThread tt = new TracingThread(vm, className);

        // used to check this, but it seems more annoying now
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
