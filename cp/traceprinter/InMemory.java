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

import java.util.regex.*;
import java.util.*;
import java.io.*;

import javax.tools.*;

import traceprinter.ramtools.*;

public class InMemory {

    String usercode;
    String mainClass;
    VirtualMachine vm;
    Map<String, byte[]> bytecode;

    public static void main(String[] args) {
        try {
            StringBuilder usercodeBuilder = new StringBuilder();
            BufferedReader br = 
                new BufferedReader(new InputStreamReader(System.in));
            
            while (br.ready()) {
                usercodeBuilder.append(br.readLine());
                usercodeBuilder.append("\n");
            }
            
            new InMemory(usercodeBuilder.toString());
        } 
        catch (IOException e) {
            System.out.print(JDI2JSON.compileErrorOutput("[could not read user code]",
                                                         "Internal IOException in php->java",
                                                         1, 1));
        }
    }

    // convenience version of JDI2JSON method
    void compileError(String msg, long row, long col) {
        try {
            PrintStream out = new PrintStream(System.out, true, "UTF-8");
            out.print(JDI2JSON.compileErrorOutput(usercode, msg, row, col));
        }
        catch (UnsupportedEncodingException e) { //fallback
            System.out.print(JDI2JSON.compileErrorOutput(usercode, msg, row, col));
        }
    }

    // figure out the class name, then compile and run main([])
    InMemory(String usercode) {
        this.usercode = usercode;

        // not 100% accurate, if people have multiple top-level classes + public inner classes
        Pattern p = Pattern.compile("public\\s+class\\s+([a-zA-Z0-9_]+)\\b");
        Matcher m = p.matcher(usercode);
        if (!m.find()) {
            compileError("Error: Make sure your code includes 'public class \u00ABClassName\u00BB'", 1, 1);
            return;
        }

        mainClass = m.group(1);

        CompileToBytes c2b = new CompileToBytes();

        c2b.compilerOutput = new StringWriter();
        c2b.options = Arrays.asList("-g -Xmaxerrs 1".split(" "));
        DiagnosticCollector<JavaFileObject> errorCollector = new DiagnosticCollector<>();
        c2b.diagnosticListener = errorCollector;

        bytecode = c2b.compileFile(mainClass, usercode);

        if (bytecode == null) {
            for (Diagnostic<? extends JavaFileObject> err : errorCollector.getDiagnostics())
                if (err.getKind() == Diagnostic.Kind.ERROR) {
                    compileError("Error: " + err.getMessage(null), Math.max(0, err.getLineNumber()),
                                 Math.max(0, err.getColumnNumber()));
                    return;
                }
            compileError("Compiler did not work, but reported no ERROR?!?!", 0, 0);
            return;
        }

        vm = launchVM("traceprinter.shoelace.NoopMain");
        vm.setDebugTraceMode(0);

        JSONTracingThread tt = new JSONTracingThread(this);
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
                    //	    System.out.println("About to call LaunchingConnector.launch...");
	    VirtualMachine result = connector.launch(args);
	    //System.out.println("...done");
            return result;
        } catch (java.io.IOException | VMStartException exc) {
	    System.out.println("Hoey!");
            System.out.println("Failed in launchTarget: " + exc.getMessage());
            exc.printStackTrace();
        } catch (IllegalConnectorArgumentsException exc) {
	    System.out.println("Hoey!");
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
