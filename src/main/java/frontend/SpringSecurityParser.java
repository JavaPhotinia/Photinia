package frontend;

import bean.HandlerModel;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JimpleLocal;
import transformer.HandlerModelEnum;
import transformer.MachineState;
import utils.SootUtil;

import java.util.*;

import static utils.StringConstantUtil.OBJECT_CLASS;

public class SpringSecurityParser extends FilterParser {
    private final SootClass filterSecurityInterceptor = Scene.v().getSootClass("org.springframework.security.web.access.intercept.FilterSecurityInterceptor");
    private final List<String> tmpPaths = new ArrayList<>();
    private final List<String> preTmpPaths = new ArrayList<>();
    private final HashMap<String, List<String>> localVariables = new HashMap<>();
    private static final Map<String, Integer> orderMap = new HashMap<>();
    private final Map<String, HandlerModel> varToHandler = new HashMap<>();
    private final Map<SootClass, HandlerModel> tmpHandlerMap = new HashMap<>();
    private final Set<SootClass> removeHandlerThroughClass = new HashSet<>();
    private final Map<String, SootClass> varToClass = new HashMap<>();
    protected final Map<Type, Map<SootClass, HandlerModel>> beanToHandlerChain = new HashMap<>();

    @Override
    protected void init(SootMethod method) {
        super.init(method);
        this.tmpHandlerMap.clear();
        findDefaultConfiguration(method.getDeclaringClass().getSuperclass());
        if (method.getParameterCount() > 0 && this.beanToHandlerChain.containsKey(method.getParameterType(0))) {
            this.tmpHandlerMap.putAll(this.beanToHandlerChain.get(method.getParameterType(0)));
        }
        this.stmtSite = 0;
    }

    private void findDefaultConfiguration(SootClass superClass) {
        if (superClass.getName().equals(OBJECT_CLASS)) return;
        for (SootMethod method : superClass.getMethods()) {
            if (method.getParameterCount() == 0 && matchConfigMethod(method)) {
                super.process(method);
                break;
            }
        }
    }

    @Override
    public boolean matchConfigMethod(SootMethod method) {
        String httpSecurity = "org.springframework.security.config.annotation.web.builders.HttpSecurity";
        if (method.getReturnType().toString().equals(httpSecurity)) {
            return true;
        }
        for (Type parameterType : method.getParameterTypes()) {
            if (parameterType.toString().equals(httpSecurity)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void caseStart(Stmt stmt) {
        initFilterOrder();
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.matchClass(method.getDeclaringClass(), "org.springframework.security.config.annotation.web.builders.HttpSecurity")) {
                if (method.getName().equals("addFilterBefore")) {
                    addFilterWithSite(stmt, -1);
                } else if (method.getName().equals("addFilterAt")) {
                    addFilterWithSite(stmt, 0);
                } else if (method.getName().equals("addFilterAfter")) {
                    addFilterWithSite(stmt, 1);
                } else if (method.getName().equals("addFilter")) {
                    addFilter(stmt.getInvokeExpr());
                } else if (method.getName().equals("antMatcher")) {
                    casePattern(stmt);
                } else {
                    parseConfigureAndFindFilter(method);
                }
                this.machineState = MachineState.START;
            } else if (method.getName().equals("disable")) {
                if (this.preTmpPaths.size() > 0) {
                    this.handlerModel.addPointcutExclude(this.preTmpPaths);
                } else {
                    this.removeHandlerThroughClass.add(this.handlerModel.getSootClass());
                }
                this.machineState = MachineState.START;
            } else if (method.getName().equals("antMatchers") || method.getName().equals("antMatcher") || method.getName().equals("anyRequest")) {
                casePattern(stmt);
            } else if (method.getName().equals("apply")) {
                SootClass configurerClass = ((RefType) stmt.getInvokeExpr().getArg(0).getType()).getSootClass();
                if (configurerClass.declaresMethodByName("configure")) {
                    for (SootMethod configureMethod : configurerClass.getMethods()) {
                        if (configureMethod.getName().equals("configure")) {
                            findHandler(configureMethod);
                            break;
                        }
                    }
                }
            } else if (matchConfigMethod(method) && method.getReturnType() instanceof VoidType && method.isPrivate()) {
                super.process(method);
            }
        } else if (stmt instanceof ReturnStmt && this.tmpHandlerMap.size() > 0) {
            Map<SootClass, HandlerModel> handlerChainMap = new HashMap<>(this.tmpHandlerMap);
            Type returnType = ((ReturnStmt) stmt).getOpBox().getValue().getType();
            addHandlerIntoChain();
            this.beanToHandlerChain.put(returnType, handlerChainMap);
            this.tmpHandlerMap.clear();
            this.handlerModel = null;
        } else if (stmt instanceof AssignStmt) {
            caseFoundHandler(stmt);
        } else if (stmt instanceof ReturnVoidStmt) {
            addHandlerIntoChain();
            this.tmpHandlerMap.clear();
            this.handlerModel = null;
        }
    }

    private void addHandlerIntoChain() {
        if (this.tmpHandlerMap.containsKey(filterSecurityInterceptor)) {
            putIntoHandlerMap();
        } else if (handlerMap.containsKey(filterSecurityInterceptor)) {
            this.handlerModel = handlerMap.get(filterSecurityInterceptor);
            putIntoHandlerMap();
        }
    }

    private void putIntoHandlerMap() {
        for (SootClass filterClass : this.tmpHandlerMap.keySet()) {
            HandlerModel filterHandler = this.tmpHandlerMap.get(filterClass);
            if (this.handlerModel.getSootClass() != filterClass) {
                cloneHandlerExpression(this.handlerModel, filterHandler);
            }
            if (handlerMap.containsKey(filterClass)) {
                cloneHandlerExpression(filterHandler, handlerMap.get(filterClass));
            } else {
                handlerMap.put(filterClass, filterHandler);
            }
        }
        for (SootClass removeHandler : this.removeHandlerThroughClass) {
            handlerMap.remove(removeHandler);
        }
    }

    private void parseConfigureAndFindFilter(SootMethod method) {
        if (method.isPhantom()) {
            return;
        }
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit instanceof JAssignStmt && unit.toString().contains("getOrApply")) {
                JimpleLocal firstArg = (JimpleLocal) ((JAssignStmt) unit).getInvokeExpr().getArg(0);
                SootClass configurerClass = ((RefType) firstArg.getType()).getSootClass();
                if (configurerClass.declaresMethodByName("configure")) {
                    for (SootMethod configureMethod : configurerClass.getMethods()) {
                        if (configureMethod.getName().equals("configure")) {
                            findHandler(configureMethod);
                            return;
                        }
                    }
                } else {
                    findHandler(configurerClass.getMethodByName("<init>"));
                    for (SootClass superClass : SootUtil.getAllSuperClasses(configurerClass)) {
                        if (superClass.declaresMethodByName("configure")) {
                            for (SootMethod configureMethod : superClass.getMethods()) {
                                if (configureMethod.getName().equals("configure")) {
                                    findHandler(configureMethod);
                                    return;
                                }
                            }
                        }
                    }
                }
                return;
            }
        }
    }

    @Override
    public void caseFoundHandler(Stmt stmt) {
        if (stmt instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) stmt;
            Value rightValue = assignStmt.getRightOp();
            Value leftValue = assignStmt.getLeftOp();
            if (rightValue instanceof StaticFieldRef) {
                StaticFieldRef fieldRef = ((StaticFieldRef) rightValue);
                if (SootUtil.matchClass(fieldRef, "org.springframework.http.HttpMethod")) {
                    this.tmpPaths.add(fieldRef.getField().getName());
                    this.machineState = MachineState.PATTERN;
                }
            } else if (assignStmt.containsInvokeExpr()) {
                Value firstValue = null;
                if (assignStmt.getInvokeExpr().getArgCount() > 0) {
                    firstValue = assignStmt.getInvokeExpr().getArg(0);
                }
                SootMethod invokeMethod = assignStmt.getInvokeExpr().getMethod();
                if (invokeMethod.getName().equals("and")) {
                    this.machineState = MachineState.START;
                } else if (!leftValue.getType().toString().contains("Filter") && firstValue != null
                        && firstValue.getType().toString().contains("Filter") && !((RefType) firstValue.getType()).getSootClass().isAbstract()) {
                    this.varToClass.put(leftValue.toString(), ((RefType) firstValue.getType()).getSootClass());
                } else if (firstValue != null && this.varToHandler.containsKey(firstValue.toString())) {
                    this.varToHandler.put(leftValue.toString(), this.varToHandler.get(firstValue.toString()));
                } else if (invokeMethod.getName().equals("anyRequest")) {
                    casePattern(stmt);
                }
            } else if (rightValue.getType() instanceof RefType && this.handlerModel != null && this.handlerModel.getSootClass() != null && rightValue instanceof JimpleLocal
                    && SootUtil.getAllSuperClasses(this.handlerModel.getSootClass()).contains(((RefType) rightValue.getType()).getSootClass())) {
                this.varToHandler.put(leftValue.toString(), this.handlerModel);
            } else if ((rightValue instanceof FieldRef || rightValue instanceof JimpleLocal)
                    && this.varToHandler.containsKey(rightValue.toString())) {
                this.varToHandler.put(leftValue.toString(), this.varToHandler.get(rightValue.toString()));
            } else if (rightValue instanceof CastExpr) {
                String rightValueName = ((CastExpr) rightValue).getOpBox().getValue().toString();
                if (this.varToHandler.containsKey(rightValueName)) {
                    this.varToHandler.put(leftValue.toString(), this.varToHandler.get(rightValueName));
                } else if (this.varToClass.containsKey(rightValueName)) {
                    this.varToClass.put(leftValue.toString(), this.varToClass.get(rightValueName));
                }
            } else if (leftValue instanceof ArrayRef && rightValue instanceof StringConstant) {
                casePattern(stmt);
            }
        } else if (stmt instanceof InvokeStmt) {
            InvokeExpr invokeStmt = stmt.getInvokeExpr();
            if (invokeStmt instanceof SpecialInvokeExpr && invokeStmt.getArgCount() > 0) {
                for (Value arg : invokeStmt.getArgs()) {
                    if (arg.getType() instanceof RefType && arg.getType().toString().contains("Filter")) {
                        SootClass filterClass = ((RefType) arg.getType()).getSootClass();
                        HandlerModel oriHandler = this.handlerModel;
                        this.handlerModel = new HandlerModel(filterClass, orderMap.get(filterClass.getShortName()), this, HandlerModelEnum.FILTER);
                        if (oriHandler != null) {
                            cloneHandlerExpression(oriHandler, this.handlerModel);
                        }
                        break;
                    }
                }
                findHandler(invokeStmt.getMethod());
            } else if (invokeStmt.getMethod().getName().equals("addFilter")) {
                addFilter(invokeStmt);
            }
        }
    }

    @Override
    public void casePattern(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (method.getName().equals("antMatchers")) {
                this.preTmpPaths.clear();
                for (Value arg : stmt.getInvokeExpr().getArgs()) {
                    if (this.localVariables.containsKey(arg.toString())) {
                        this.tmpPaths.addAll(this.localVariables.get(arg.toString()));
                    }
                }
                String finalPath = String.join("+", this.tmpPaths);
                this.handlerModel.addPointcutExpressions(finalPath);
                this.machineState = MachineState.PATTERN;
                this.preTmpPaths.add(finalPath);
                tmpPaths.clear();
                this.localVariables.clear();
            } else if (method.getName().equals("antMatcher")) {
                if (this.handlerModel == null) {
                    this.handlerModel = new HandlerModel(this);
                    this.handlerModel.setHandlerModelEnum(HandlerModelEnum.FILTER);
                }
                if (stmt.getInvokeExpr().getArg(0) instanceof StringConstant) {
                    this.preTmpPaths.clear();
                    String argValue = ((StringConstant) stmt.getInvokeExpr().getArg(0)).value;
                    this.handlerModel.addPointcutExpressions(argValue);
                    this.preTmpPaths.add(argValue);
                }
                this.machineState = MachineState.PATTERN;
                tmpPaths.clear();
                this.localVariables.clear();
            } else if (method.getName().equals("hasRole") && stmt.getInvokeExpr().getArgs().size() == 1) {
                caseAntMachersX(stmt, "hasRole", true);
            } else if (method.getName().equals("hasAnyRole")) {
                caseAntMachersX(stmt, "hasAnyRole", false);
            } else if (method.getName().equals("hasAuthority") && stmt.getInvokeExpr().getArgs().size() == 1) {
                caseAntMachersX(stmt, "hasAuthority", true);
            } else if (method.getName().equals("hasAnyAuthority")) {
                caseAntMachersX(stmt, "hasAnyAuthority", false);
            } else if (method.getName().equals("access") && stmt.getInvokeExpr().getArgs().size() == 1) {
                caseAntMatchersAccess(stmt);
            } else if (method.getName().equals("permitAll")) {
                caseAntMatchersPermitAll();
            } else if (method.getName().equals("anyRequest")) {
                this.handlerModel.addPointcutExpressions("/**");
                this.machineState = MachineState.START;
                this.preTmpPaths.clear();
            } else if (method.getName().equals("and")) {
                this.machineState = MachineState.START;
            } else if (SootUtil.matchClass(method.getDeclaringClass(), "org.springframework.security.config.annotation.web.builders.HttpSecurity")) {
                caseStart(stmt);
            }
        } else if (stmt instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) stmt;
            Value rightValue = assignStmt.getRightOp();
            Value leftValue = assignStmt.getLeftOp();
            if (rightValue.getType() instanceof ArrayType) {
                ArrayType arrayType = (ArrayType) rightValue.getType();
                if (arrayType.getArrayElementType() instanceof RefType) {
                    RefType refType = (RefType) arrayType.getArrayElementType();
                    if (refType.getClassName().equals("java.lang.String") && leftValue instanceof Local) {
                        Local localVar = (Local) leftValue;
                        this.localVariables.put(localVar.getName().replaceAll("\\[[^)]*]", ""), new LinkedList<>());
                    }
                }
            } else if (leftValue instanceof ArrayRef && rightValue instanceof StringConstant) {
                ArrayRef arrayRef = (ArrayRef) leftValue;
                String arrayVar = arrayRef.getBaseBox().getValue() instanceof Local ? ((Local) arrayRef.getBaseBox().getValue()).getName() : "";
                if (this.localVariables.containsKey(arrayVar)) {
                    this.localVariables.get(arrayVar).add(((StringConstant) rightValue).value);
                } else {
                    List<String> paths = new ArrayList<>();
                    paths.add(((StringConstant) rightValue).value);
                    this.localVariables.put(arrayVar, paths);
                }
            }
        }
    }

    private void addFilter(InvokeExpr invokeStmt) {
        HandlerModel oriHandler = this.handlerModel;
        Value firstArg = invokeStmt.getArg(0);
        if (varToHandler.containsKey(firstArg.toString())) {
            this.handlerModel = varToHandler.get(firstArg.toString());
        } else {
            SootClass filterClass = ((RefType) firstArg.getType()).getSootClass();
            if (this.tmpHandlerMap.containsKey(filterClass)) {
                HandlerModel tmpHandler = tmpHandlerMap.get(filterClass);
                if (this.handlerModel != null) {
                    cloneHandlerExpression(this.handlerModel, tmpHandler);
                }
                this.handlerModel = tmpHandler;
                return;
            } else if (filterClass.isInterface() || filterClass.isAbstract()) {
                if (this.varToClass.containsKey(firstArg.toString())) {
                    filterClass = this.varToClass.get(firstArg.toString());
                } else {
                    return;
                }
            }
            this.handlerModel = new HandlerModel(filterClass, orderMap.get(filterClass.getShortName()), this, HandlerModelEnum.FILTER);
        }
        if (oriHandler != null) {
            cloneHandlerExpression(oriHandler, this.handlerModel);
        }
        this.tmpHandlerMap.put(this.handlerModel.getSootClass(), this.handlerModel);
        this.varToHandler.clear();
    }

    private void addFilterWithSite(Stmt stmt, int site) {
        SootClass addFilter = ((RefType) stmt.getInvokeExpr().getArgBox(0).getValue().getType()).getSootClass();
        if (tmpHandlerMap.containsKey(addFilter)) {
            return;
        }
        String targetFilterString = transferClassName(stmt.getInvokeExpr().getArgBox(1).getValue().toString());
        int targetOrder = orderMap.get(Scene.v().getSootClass(targetFilterString).getShortName());
        HandlerModel oriHandler = this.handlerModel;
        this.handlerModel = new HandlerModel(addFilter, targetOrder + site, this, HandlerModelEnum.FILTER);
        if (oriHandler != null) {
            cloneHandlerExpression(oriHandler, this.handlerModel);
        }
        tmpHandlerMap.put(this.handlerModel.getSootClass(), this.handlerModel);
        varToHandler.clear();
    }

    private void initFilterOrder() {
        if (orderMap.size() > 0) {
            return;
        }
        orderMap.put("DisableEncodeUrlFilter", 100);
        orderMap.put("ForceEagerSessionCreationFilter", 200);
        orderMap.put("ChannelProcessingFilter", 300);
        orderMap.put("WebAsyncManagerIntegrationFilter", 500);
        orderMap.put("SecurityContextHolderFilter", 600);
        orderMap.put("SecurityContextPersistenceFilter", 700);
        orderMap.put("HeaderWriterFilter", 800);
        orderMap.put("CorsFilter", 900);
        orderMap.put("CsrfFilter", 1000);
        orderMap.put("LogoutFilter", 1100);
        orderMap.put("OAuth2AuthorizationRequestRedirectFilter", 1200);
        orderMap.put("Saml2WebSsoAuthenticationRequestFilter", 1300);
        orderMap.put("X509AuthenticationFilter", 1400);
        orderMap.put("AbstractPreAuthenticatedProcessingFilter", 1500);
        orderMap.put("CasAuthenticationFilter", 1600);
        orderMap.put("OAuth2LoginAuthenticationFilter", 1700);
        orderMap.put("Saml2WebSsoAuthenticationFilter", 1800);
        orderMap.put("UsernamePasswordAuthenticationFilter", 1900);
        orderMap.put("OpenIDAuthenticationFilter", 2100);
        orderMap.put("DefaultLoginPageGeneratingFilter", 2200);
        orderMap.put("DefaultLogoutPageGeneratingFilter", 2300);
        orderMap.put("ConcurrentSessionFilter", 2400);
        orderMap.put("DigestAuthenticationFilter", 2500);
        orderMap.put("BearerTokenAuthenticationFilter", 2600);
        orderMap.put("BasicAuthenticationFilter", 2700);
        orderMap.put("RequestCacheAwareFilter", 2800);
        orderMap.put("SecurityContextHolderAwareRequestFilter", 2900);
        orderMap.put("JaasApiIntegrationFilter", 3000);
        orderMap.put("RememberMeAuthenticationFilter", 3100);
        orderMap.put("AnonymousAuthenticationFilter", 3200);
        orderMap.put("OAuth2AuthorizationCodeGrantFilter", 3300);
        orderMap.put("SessionManagementFilter", 3400);
        orderMap.put("ExceptionTranslationFilter", 3500);
        orderMap.put("FilterSecurityInterceptor", 3600);
        orderMap.put("AuthorizationFilter", 3700);
        orderMap.put("SwitchUserFilter", 3800);
    }

    private void findHandler(SootMethod sootMethod) {
        this.varToClass.clear();
        if (sootMethod.isJavaLibraryMethod() || sootMethod.isAbstract() || sootMethod.isPhantom()) {
            return;
        }
        for (Unit unit : sootMethod.retrieveActiveBody().getUnits()) {
            if (unit instanceof AssignStmt || unit instanceof InvokeStmt) {
                caseFoundHandler((Stmt) unit);
            }
        }
    }

    private void caseAntMachersX(Stmt stmt, String method, boolean singleArg) {
        StringBuilder authExpression;
        if (singleArg) {
            StringConstant stringConstant = (StringConstant) stmt.getInvokeExpr().getArg(0);
            authExpression = new StringBuilder(method + "('" + stringConstant.value + "')");
        } else {
            Local argVar = (Local) stmt.getInvokeExpr().getArg(0);
            List<String> authPerms = this.localVariables.get(argVar.getName());
            authExpression = new StringBuilder(method + "(");
            for (int i = 0; i < authPerms.size(); i++) {
                authExpression.append("'").append(authPerms.get(i)).append("'");
                if (i != authPerms.size() - 1) {
                    authExpression.append(", ");
                }
            }
            authExpression.append(")");
        }
        this.handlerModel.setRoleString(authExpression + ":" + this.preTmpPaths);
        this.machineState = MachineState.FOUND_HANDLER;
        this.localVariables.clear();
    }

    private void caseAntMatchersAccess(Stmt stmt) {
        Value first = stmt.getInvokeExpr().getArg(0);
        if (first instanceof Local) {
        } else {
            StringConstant authExpression = (StringConstant) stmt.getInvokeExpr().getArg(0);
            this.handlerModel.setRoleString(authExpression.value);
            this.machineState = MachineState.FOUND_HANDLER;
        }
    }

    private void caseAntMatchersPermitAll() {
        this.handlerModel.addPointcutExclude(this.preTmpPaths);
        this.handlerModel.getPointcutExpressions().removeAll(this.preTmpPaths);
        tmpPaths.clear();
        this.machineState = MachineState.START;
    }

    private String transferClassName(String ori) {
        return ori.replace("/", ".").replace("class \"L", "").replace(";\"", "");
    }

    private void cloneHandlerExpression(HandlerModel oriModel, HandlerModel newModel) {
        newModel.addPointcutExclude(oriModel.getPointcutExcludes());
        newModel.addPointcutExpressions(oriModel.getPointcutExpressions());
    }
}
