package ouptut;

import com.alibaba.fastjson.JSONObject;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SceneTransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ForwardTransformer extends SceneTransformer {
    public static String jarLoc = null;
    public static CallGraph cg = null;

    public ForwardTransformer(String jarLoc) {
        ForwardTransformer.jarLoc = jarLoc;
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> map) {

        cg = Scene.v().getCallGraph();
        int count = 0;
        int clintnum = 0;
        Iterator<Edge> iterator = cg.listener();
        Set<String> sinkMethod = new HashSet<>();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            JSONObject jsonObject = new JSONObject();

            try {
                if (edge.getSrc().method().toString().contains("com.salesmanager.")
                        || edge.getTgt().method().toString().contains("com.salesmanager.")
                        || edge.getSrc().method().toString().contains("doFilter")
                        || edge.getTgt().method().toString().contains("doFilter")
                        || edge.getSrc().method().toString().contains("Interceptor")
                        || edge.getTgt().method().toString().contains("Interceptor")
                        || edge.getTgt().method().toString().contains("synthetic.method")
                        || edge.getSrc().method().toString().contains("synthetic.method")) {
                    jsonObject.put("srcMethod", edge.getSrc().method().toString());
                    jsonObject.put("tgtMethod", edge.getTgt().method().toString());
                    // System.out.println(jsonObject);
                    count++;
                    if (edge.getSrc().method().toString().contains("<clinit>")
                            || edge.getTgt().method().toString().contains("<clinit>")) {
                        clintnum++;
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        ReachableMethods reachableMethods = Scene.v().getReachableMethods();

        Iterator<MethodOrMethodContext> methodIterator = reachableMethods.listener();
        Set<String> methods = new HashSet<>();
        while (methodIterator.hasNext()) {
            MethodOrMethodContext methodOrMethodContext = methodIterator.next();
            if (methodOrMethodContext.method().toString().contains("org.jeecg.") && !methodOrMethodContext.method().getName().contains("synthetic")) {
                methods.add(methodOrMethodContext.method().toString());
            }
        }
        System.out.println("total reachableMethods : " + reachableMethods.size());
        System.out.println("reachable Method : " + methods.size());
        System.out.println("edge total: " + count);
        System.out.println("edge clinit: " + clintnum);
    }
}
