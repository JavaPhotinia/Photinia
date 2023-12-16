package frontend;

import analysis.CreateEdge;
import bean.HandlerModel;
import bean.HandlerTargetModel;
import bean.MappingValueModel;
import org.springframework.util.AntPathMatcher;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JReturnStmt;
import soot.tagkit.AbstractHost;
import transformer.MachineState;
import utils.FactoryUtil;
import utils.XMLDocumentHolder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CoRParser {
    protected HandlerModel handlerModel;
    protected MachineState machineState;
    public static Map<String, HandlerTargetModel> handlerAndTargetModelMap = new HashMap<>();
    private final FactoryUtil factoryUtil = new FactoryUtil();
    protected SootMethod targetHandlerMethod;
    protected Integer stmtSite = 0;
    protected Map<String, List<String>> varAndString = new HashMap<>();
    public static final Map<SootClass, HandlerModel> handlerMap = new HashMap<>();
    private final Map<String, Pattern> patternMap = new HashMap<>();
    private final AnnotationAnalysis annotationAnalysis = new AnnotationAnalysis();
    protected AntPathMatcher antPathMatcher = new AntPathMatcher();

    public void process(AbstractHost host) {
        init((SootMethod) host);
        processMethodBody((SootMethod) host);
        this.stmtSite = 0;
    }

    protected void init(SootMethod method) {
        this.targetHandlerMethod = method;
        this.varAndString.clear();
        this.handlerModel = null;
        this.machineState = MachineState.START;
        this.stmtSite = 0;
    }

    private void processMethodBody(SootMethod method) {
        if (method.isAbstract() || method.isPhantom()) {
            return;
        }
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit instanceof AssignStmt || unit instanceof InvokeStmt || unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt) {
                caseAssignStmt((Stmt) unit);
            }
            stmtSite++;
        }
    }

    abstract public boolean matchConfigMethod(SootMethod method);

    public void savePointMethod(HandlerModel handlerModel, SootClass sootClass, SootMethod method) {
        if (method.getName().equals("<init>") || method.getName().equals("callEntry_synthetic")
                || method.getName().equals("<clinit>") || method.isStatic() | method.isPrivate() || method.isFinal()) {
            return;
        }
        SootClass targetClass = sootClass;
        SootMethod targetMethod = method;
        if (targetClass.isInterface()) {
            targetClass = CreateEdge.interfaceToBeans.get(targetClass.getName());
            targetMethod = targetClass.getMethodUnsafe(targetMethod.getSubSignature());
        }
        if (targetMethod != null) {
            String methodSign = targetMethod.getSignature();
            if (!handlerAndTargetModelMap.containsKey(methodSign)) {
                HandlerTargetModel htm = factoryUtil.getAopTargetInstance(targetClass, targetMethod);
                htm.addHandlerIntoChain(handlerModel);
                htm.addFrameworkCategory(handlerModel.getHandlerModelEnum());
                handlerAndTargetModelMap.put(methodSign, htm);
            } else {
                HandlerTargetModel htm = handlerAndTargetModelMap.get(methodSign);
                htm.addHandlerIntoChain(handlerModel);
                htm.addFrameworkCategory(handlerModel.getHandlerModelEnum());
            }
        }
    }

    public void caseAssignStmt(Stmt stmt) {
        switch (machineState) {
            case START:
                caseStart(stmt);
                break;
            case FOUND_HANDLER:
                caseFoundHandler(stmt);
                break;
            case PATTERN:
                casePattern(stmt);
                break;
            // case MATCHERS:
            //     constructHandlerChain();
            //     break;
        }
    }

    abstract public void caseStart(Stmt stmt);

    abstract public void caseFoundHandler(Stmt stmt);

    abstract public void casePattern(Stmt stmt);

    public void constructHandlerChain(HandlerModel handler) {
        if (handler.getSootClass() == null && handler.getSootClass().isInterface()) {
            System.out.println("Missing Handler: " + handler);
            return;
        }
        putAddPathPattern(handler);
        for (SootClass controller : AnnotationAnalysis.controllers) {
            for (SootMethod controllerMethod : controller.getMethods()) {
                MappingValueModel mvm = annotationAnalysis.getMappingAnnotationValue(controllerMethod);
                String urlPath = annotationAnalysis.getMappingAnnotationValue(controller).getPath() + mvm.getPath();
                String methodHTTPTag = mvm.getHttpMethod();
                if (!mvm.getPath().equals("") && pathMatch(handler.getPointcutExpressions(), methodHTTPTag, urlPath)
                        && !pathMatch(handler.getPointcutExcludes(), methodHTTPTag, urlPath)) {
                    addPathForMethod(urlPath, controllerMethod, handler);
                }
            }
        }
    }

    void putAddPathPattern(HandlerModel handlerModel) {
        if (handlerModel.getPointcutExpressions().size() == 0) {
            handlerModel.addPointcutExpressions("/**");
        }
    }

    boolean pathMatch(List<String> pathPatterns, String httpMethod, String urlPath) {
        if (!urlPath.startsWith("/")) {
            urlPath = "/" + urlPath;
        }
        boolean returnFlag = false;
        for (String pathPattern : pathPatterns) {
            if (antPathMatcher.match(pathPattern, urlPath) || antPathMatcher.match(pathPattern, httpMethod)) {
                returnFlag = true;
                break;
            }
        }
        return returnFlag;
    }

    abstract void addPathForMethod(String path, SootMethod method, HandlerModel handlerModel);

    public Value getOrder(SootMethod method) {
        JReturnStmt lastStmt = (JReturnStmt) method.retrieveActiveBody().getUnits().getLast();
        return lastStmt.getOpBox().getValue();
    }

    public Value getOrder(SootMethod method, Value value) {
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit.toString().contains(value.toString())) {
                return ((AssignStmt) unit).getRightOp();
            }
        }
        return null;
    }

    public SootClass findReturnConcreteClass(SootMethod method) {
        JReturnStmt lastStmt = (JReturnStmt) method.retrieveActiveBody().getUnits().getLast();
        RefType returnType = (RefType) lastStmt.getOpBox().getValue().getType();
        return returnType.getSootClass();
    }

    public void addPatternURL(String stack, String patternURL) {
        if (varAndString.containsKey(stack)) {
            varAndString.get(stack).add(patternURL);
        } else {
            List<String> paths = new ArrayList<>();
            paths.add(patternURL);
            varAndString.put(stack, paths);
        }
    }

    public void addPatternURL(String stack, List<String> patternURLs) {
        if (varAndString.containsKey(stack)) {
            varAndString.get(stack).addAll(patternURLs);
        } else {
            List<String> paths = new ArrayList<>(patternURLs);
            varAndString.put(stack, paths);
        }
    }

    public boolean pathIsMatch(String pathPattern, String path) {
        if (path.contains(pathPattern.replace("*", ""))) {
            return true;
        }
        Pattern compile;
        pathPattern = pathPattern.replace("{", "\\{").replace("}", "\\}");
        if (!patternMap.containsKey(pathPattern)) {
            Pattern re = Pattern.compile("\\*\\*");
            StringBuilder sb = new StringBuilder();
            sb.append("^");
            Matcher matcher = re.matcher(pathPattern);
            if (matcher.find()) {
                String afterPathPattern = pathPattern.replace("**", "([a-z|A-Z|_|/|{|}]+[0-9]*)\\.");
                sb.append(afterPathPattern);
            } else {
                sb.append(pathPattern);
            }

            String res = sb.toString();
            if (res.lastIndexOf(".") == res.length() - 1) {
                res = res.substring(0, res.lastIndexOf("\\."));
            }
            res += "$";
            compile = Pattern.compile(res);
            patternMap.put(pathPattern, compile);
        } else {
            compile = patternMap.get(pathPattern);
        }
        Matcher pathMatcher = compile.matcher(path);
        return pathMatcher.find();
    }

    public XMLDocumentHolder getXMLHolder(Set<String> xmlpaths) {
        if (xmlpaths.size() == 0) {
            return null;
        }
        XMLDocumentHolder holder = new XMLDocumentHolder();
        for (String xmlpath : xmlpaths) {
            org.dom4j.Document document = holder.getDocument(xmlpath);
            if (document != null) {
                holder.addElements(document);
                holder.hasFilter(document);
                holder.hasShiroConfig(document);
            }
        }
        return holder;
    }
}
