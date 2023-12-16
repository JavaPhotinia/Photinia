package frontend;

import analysis.CreateEdge;
import bean.HandlerModel;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.ClassConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import transformer.HandlerModelEnum;
import transformer.MachineState;
import utils.SootUtil;
import utils.XMLDocumentHolder;

import java.util.*;

public class ShiroParser extends FilterParser {
    private final Map<String, HandlerModel> nameAndHandler = new HashMap<>();
    private final Map<String, List<String>> authNameAndPaths = new HashMap<>();
    private final SootClass shiroFilterFactoryBean = Scene.v().getSootClass("org.apache.shiro.spring.web.ShiroFilterFactoryBean");
    private final SootClass defaultFilter = Scene.v().getSootClass("org.apache.shiro.web.filter.mgt.DefaultFilter");
    private final SootClass shiroAbstractFilter = Scene.v().getSootClass("org.apache.shiro.web.servlet.OncePerRequestFilter");

    @Override
    public boolean matchConfigMethod(SootMethod method) {
        return SootUtil.matchType(method.getReturnType(), "org.apache.shiro.spring.web.ShiroFilterFactoryBean");
    }

    @Override
    public void caseStart(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.getAllSuperClasses(method.getDeclaringClass()).contains(shiroFilterFactoryBean) && method.getName().equals("<init>")) {
                SootMethod createInstanceMethod = method.getDeclaringClass().getMethodByName("createInstance");
                CreateEdge.beansAndMethods.put(((RefType) createInstanceMethod.getReturnType()).getSootClass(), createInstanceMethod);
                this.process(defaultFilter.getMethodByName("<clinit>"));
                this.process(createInstanceMethod);
                SootClass returnClass = ((RefType) createInstanceMethod.getReturnType()).getSootClass();
                SootClass concreteClass = findReturnConcreteClass(createInstanceMethod);
                if (handlerMap.containsKey(returnClass)) {
                    handlerMap.get(returnClass).setSootClass(concreteClass);
                } else {
                    this.handlerModel = new HandlerModel(concreteClass, 1, this, HandlerModelEnum.FILTER);
                    handlerMap.put(returnClass, this.handlerModel);
                }
                this.machineState = MachineState.PATTERN;
            } else if (SootUtil.matchClassAndMethod(method, defaultFilter.getName(), "<init>")) {
                caseFoundHandler(stmt);
            } else if (method.getName().equals("setFilterChainDefinitionMap")) {
                caseFoundHandler(stmt);
            }
        }
    }

    @Override
    public void caseFoundHandler(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.matchClassAndMethod(method, "java.util.Map", "put") && stmt.getInvokeExpr().getArg(0) instanceof StringConstant) {
                String zeroArgValue = ((StringConstant) stmt.getInvokeExpr().getArg(0)).value;
                SootClass possible = ((RefType) stmt.getInvokeExpr().getArg(1).getType()).getSootClass();
                if (SootUtil.getAllSuperClasses(possible).contains(shiroAbstractFilter)) {
                    this.handlerModel = new HandlerModel(possible, Integer.MAX_VALUE - 1, this, HandlerModelEnum.FILTER);
                    this.nameAndHandler.put(zeroArgValue, this.handlerModel);
                    this.machineState = MachineState.FOUND_HANDLER;
                }
            } else if (SootUtil.matchMethod(method, "void setFilters(java.util.Map)")) {
                handlerMap.put(this.handlerModel.getSootClass(), this.handlerModel);
                this.machineState = MachineState.PATTERN;
            } else if (SootUtil.matchClassAndMethod(method, defaultFilter.getName(), "<init>")) {
                String zeroArgValue = ((StringConstant) stmt.getInvokeExpr().getArg(0)).value;
                String secondArgValue = ((ClassConstant) stmt.getInvokeExpr().getArg(2)).value;
                String filterClassName = secondArgValue.replace("/", ".").replace(";", "");
                if (filterClassName.startsWith("L")) {
                    filterClassName = filterClassName.substring(1);
                }
                SootClass filterClass = Scene.v().getSootClass(filterClassName);
                this.handlerModel = new HandlerModel(filterClass, Integer.MAX_VALUE - 1, this, HandlerModelEnum.FILTER);
                this.nameAndHandler.put(zeroArgValue, this.handlerModel);
                this.machineState = MachineState.FOUND_HANDLER;
            } else if (method.getName().equals("setFilterChainDefinitionMap")) {
                for (String authName : this.authNameAndPaths.keySet()) {
                    if (this.nameAndHandler.containsKey(authName)) {
                        HandlerModel handler = this.nameAndHandler.get(authName);
                        handler.addPointcutExpressions(this.authNameAndPaths.get(authName));
                        if (!handlerMap.containsKey(handler.getSootClass())) {
                            handlerMap.put(handler.getSootClass(), handler);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void casePattern(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (method.getName().equals("put")
                    && SootUtil.getSuperClassesAndInterfaces(method.getDeclaringClass()).contains(Scene.v().getSootClass("java.util.Map"))
                    && stmt.getInvokeExpr().getArg(0) instanceof StringConstant) {
                String zeroArgValue = ((StringConstant) stmt.getInvokeExpr().getArg(0)).value;
                if (stmt.getInvokeExpr().getArg(1) instanceof StringConstant) {
                    String firstArgValue = ((StringConstant) stmt.getInvokeExpr().getArg(1)).value;
                    for (String unit : split(firstArgValue, ',', '[', ']', true, true)) {
                        unit = unit.split("\\[")[0];
                        if (this.authNameAndPaths.containsKey(unit)) {
                            this.authNameAndPaths.get(unit).add(zeroArgValue);
                        } else {
                            List<String> pathSet = new ArrayList<>();
                            pathSet.add(zeroArgValue);
                            this.authNameAndPaths.put(unit, pathSet);
                        }
                    }
                } else if (stmt.getInvokeExpr().getArg(1).getType() instanceof RefType) {
                    caseFoundHandler(stmt);
                }
            } else if (method.getName().equals("setFilterChainDefinitionMap")) {
                caseFoundHandler(stmt);
            }
        }
    }

    public String[] split(String aLine, char delimiter, char beginQuoteChar, char endQuoteChar, boolean retainQuotes, boolean trimTokens) {
        String line = clean(aLine);
        if (line == null) {
            return null;
        } else {
            List<String> tokens = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            boolean inQuotes = false;

            for (int i = 0; i < line.length(); ++i) {
                char c = line.charAt(i);
                if (c == beginQuoteChar) {
                    if (inQuotes && line.length() > i + 1 && line.charAt(i + 1) == beginQuoteChar) {
                        sb.append(line.charAt(i + 1));
                        ++i;
                    } else {
                        inQuotes = !inQuotes;
                        if (retainQuotes) {
                            sb.append(c);
                        }
                    }
                } else if (c == endQuoteChar) {
                    inQuotes = !inQuotes;
                    if (retainQuotes) {
                        sb.append(c);
                    }
                } else if (c == delimiter && !inQuotes) {
                    String s = sb.toString();
                    if (trimTokens) {
                        s = s.trim();
                    }

                    tokens.add(s);
                    sb = new StringBuilder();
                } else {
                    sb.append(c);
                }
            }

            String s = sb.toString();
            if (trimTokens) {
                s = s.trim();
            }
            tokens.add(s);
            return tokens.toArray(new String[tokens.size()]);
        }
    }

    public String clean(String in) {
        String out = in;
        if (in != null) {
            out = in.trim();
            if (out.equals("")) {
                out = null;
            }
        }
        return out;
    }

    @Override
    public void getXMLFilterSootClazzes(Set<String> xmlpaths) {
        XMLDocumentHolder holder = getXMLHolder(xmlpaths);
        if (holder == null) {
            return;
        }
        this.process(defaultFilter.getMethodByName("<clinit>"));
        Map<String, String> allShiroMap = holder.getShiroMap();
        for (String key : allShiroMap.keySet()) {
            String value = allShiroMap.get(key);
            HandlerModel handler;
            if (this.nameAndHandler.containsKey(value)) {
                handler = this.nameAndHandler.get(value);
            } else if (value.startsWith("bean:")) {
                String beanName = SootUtil.upperFirst(value.replace("bean:", ""));
                SootMethod method = Scene.v().getSootClass("synthetic.method.SingletonFactory").getMethodByName("get" + beanName);
                handler = new HandlerModel(((RefType) method.getReturnType()).getSootClass(), Integer.MAX_VALUE, new ShiroParser(), HandlerModelEnum.FILTER);
            } else {
                handler = new HandlerModel(Scene.v().getSootClass(value), Integer.MAX_VALUE, new ShiroParser(), HandlerModelEnum.FILTER);
            }
            if (handlerMap.containsKey(handler.getSootClass())) {
                handlerMap.get(handler.getSootClass()).addPointcutExpressions(key);
            } else {
                handler.addPointcutExpressions(key);
                handlerMap.put(handler.getSootClass(), handler);
            }
        }
    }
}
