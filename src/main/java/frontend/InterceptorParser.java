package frontend;

import bean.HandlerModel;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JimpleLocal;
import transformer.HandlerEnum;
import transformer.HandlerModelEnum;
import transformer.MachineState;
import utils.SootUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.StringConstantUtil.INTERCEPTOR_REGISTRATION_CLASS;
import static utils.StringConstantUtil.INTERCEPTOR_REGISTRY_CLASS;

public class InterceptorParser extends CoRParser {
    private final SootClass webMvcConfigurer = Scene.v().getSootClass("org.springframework.web.servlet.config.annotation.WebMvcConfigurer");
    private final SootClass handlerMapping = Scene.v().getSootClass("org.springframework.web.servlet.HandlerMapping");
    private final SootClass handlerInterceptor = Scene.v().getSootClass("org.springframework.web.servlet.HandlerInterceptor");
    private int tmpOrder = Integer.MAX_VALUE;
    private final Map<String, SootMethod> varToMethod = new HashMap<>();
    private final HashMap<String, Value> localVariable = new HashMap<>();

    @Override
    protected void init(SootMethod method) {
        super.init(method);
        this.localVariable.clear();
    }

    @Override
    public boolean matchConfigMethod(SootMethod method) {
        if (method.getReturnType() instanceof RefType
                && (SootUtil.getSuperClassesAndInterfaces(((RefType) method.getReturnType()).getSootClass()).contains(webMvcConfigurer)
                || SootUtil.getSuperClassesAndInterfaces(((RefType) method.getReturnType()).getSootClass()).contains(handlerMapping))) {
            return true;
        }

        if (method.getDeclaringClass().getName().contains("$lambda")) {
            return false;
        }

        for (Type parameterType : method.getParameterTypes()) {
            if (parameterType instanceof RefType && ((RefType) parameterType).getClassName().equals(INTERCEPTOR_REGISTRY_CLASS)) {
                return true;
            }
        }
        return false;
    }

    public void putConfigToScope(HandlerModel handlerModel, SootMethod adviceMethod) {
        switch (adviceMethod.getName()) {
            case "preHandle":
                handlerModel.setAnnotation(HandlerEnum.PRE_CODE);
                break;
            case "postHandle":
                handlerModel.setAnnotation(HandlerEnum.POST_CODE);
                break;
            case "afterCompletion":
                handlerModel.setAnnotation(HandlerEnum.COMPLETION);
                break;
        }
    }

    @Override
    public void caseStart(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            caseFoundHandler(stmt);
        } else if (stmt instanceof JAssignStmt) {
            if (((JAssignStmt) stmt).getRightOp() instanceof StringConstant || ((JAssignStmt) stmt).getRightOp() instanceof JInstanceFieldRef) {
                casePattern(stmt);
            }
        } else if (stmt instanceof ReturnStmt && ((ReturnStmt) stmt).getOpBox().getValue().getType() instanceof RefType) {
            SootClass returnClass = ((RefType) ((ReturnStmt) stmt).getOpBox().getValue().getType()).getSootClass();
            for (SootMethod method : returnClass.getMethods()) {
                if (matchConfigMethod(method)) {
                    this.process(method);
                    break;
                }
            }
        }
    }

    @Override
    public void caseFoundHandler(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.matchClassAndMethod(method, INTERCEPTOR_REGISTRY_CLASS, "addInterceptor")) {
                RefType argType = (RefType) stmt.getInvokeExpr().getArg(0).getType();
                if (!argType.getSootClass().isInterface()) {
                    this.handlerModel = new HandlerModel(argType.getSootClass(), this.tmpOrder, this, HandlerModelEnum.INTERCEPTOR);
                }
                handlerMap.put(this.handlerModel.getSootClass(), this.handlerModel);
                this.tmpOrder = Integer.MAX_VALUE;
                this.machineState = MachineState.PATTERN;
            } else if (method.getName().equals("setInterceptors")) {
                if (this.varToMethod.containsKey(stmt.getInvokeExpr().getArg(0).toString())) {
                    this.process(this.varToMethod.get(stmt.getInvokeExpr().getArg(0).toString()));
                }
                this.machineState = MachineState.PATTERN;
            } else if (method.getName().equals("loginProcessingUrl")) {
                // tmpPaths.add(((StringConstant) stmt.getInvokeExpr().getArg(0)).value);
            } else if (method.getName().equals("setOrder")) {
                Value argValue = stmt.getInvokeExpr().getArg(0);
                if (argValue instanceof JimpleLocal) {
                    if (this.localVariable.containsKey(argValue.toString())) {
                        Value rightValue = this.localVariable.get(argValue.toString());
                        if (rightValue instanceof JInstanceFieldRef) {
                            for (SootMethod sootMethod : ((JInstanceFieldRef) rightValue).getField().getDeclaringClass().getMethods()) {
                                if (sootMethod.getName().equals("<init>")) {
                                    argValue = getOrder(sootMethod, rightValue);
                                    break;
                                }
                            }
                        }
                    } else {
                        List<Unit> unitList = new ArrayList<>(this.targetHandlerMethod.retrieveActiveBody().getUnits());
                        for (int i = 1; i < this.stmtSite; i++) {
                            Stmt assignStmt = (Stmt) unitList.get(this.stmtSite - i);
                            if (assignStmt.containsInvokeExpr()) {
                                argValue = getOrder(assignStmt.getInvokeExpr().getMethod());
                                break;
                            }
                        }
                    }
                }
                if (argValue == null || argValue.toString().startsWith("$")) {
                    this.tmpOrder = Integer.MAX_VALUE;
                } else {
                    this.tmpOrder = Integer.parseInt(argValue.toString());
                }
                this.machineState = MachineState.FOUND_HANDLER;
            } else if (matchConfigMethod(method)) {
                this.process(method);
            } else if (stmt instanceof JAssignStmt) {
                JAssignStmt assignStmt = (JAssignStmt) stmt;
                if (method.getReturnType() instanceof ArrayType) {
                    this.varToMethod.put(assignStmt.getLeftOp().toString(), method);
                    this.machineState = MachineState.FOUND_HANDLER;
                } else if (method.getReturnType() instanceof RefType
                        && SootUtil.getAllSuperClasses(((RefType) method.getReturnType()).getSootClass()).contains(handlerInterceptor)) {
                    JReturnStmt lastStmt = (JReturnStmt) method.retrieveActiveBody().getUnits().getLast();
                    SootClass handlerClass = ((RefType)lastStmt.getOpBox().getValue().getType()).getSootClass();
                    this.handlerModel = new HandlerModel(handlerClass, this.tmpOrder, this, HandlerModelEnum.INTERCEPTOR);
                    this.machineState = MachineState.FOUND_HANDLER;
                }
                // if (assignStmt.getRightOp().getType() instanceof ArrayType) {
                //     this.machineState = MachineState.PATTERN;
                // } else
            }
        } else if (stmt instanceof JAssignStmt) {
            if (((JAssignStmt) stmt).getRightOp() instanceof StringConstant || ((JAssignStmt) stmt).getRightOp() instanceof JInstanceFieldRef) {
                casePattern(stmt);
            }
        }
    }

    @Override
    public void casePattern(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.matchClass(method.getDeclaringClass(), INTERCEPTOR_REGISTRATION_CLASS)) {
                String zeroArgName = stmt.getInvokeExpr().getArg(0).toString();
                if (method.getName().equals("addPathPatterns")) {
                    if (varAndString.containsKey(zeroArgName)) {
                        this.handlerModel.addPointcutExpressions(varAndString.get(zeroArgName));
                    }
                } else if (method.getName().equals("excludePathPatterns")) {
                    if (varAndString.containsKey(zeroArgName)) {
                        this.handlerModel.addPointcutExclude(varAndString.get(zeroArgName));
                    }
                } else {
                    return;
                }
                this.machineState = MachineState.PATTERN;
            } else if (SootUtil.matchClassAndMethod(method, INTERCEPTOR_REGISTRY_CLASS, "addInterceptor")) {
                caseFoundHandler(stmt);
            }
        } else if (stmt instanceof AssignStmt) {
            AssignStmt aStmt = (AssignStmt) stmt;
            Value rightValue = aStmt.getRightOp();
            Value leftValue = aStmt.getLeftOp();
            if (rightValue instanceof StringConstant) {
                addPatternURL(leftValue.toString().replaceAll("\\[[^)]*]", ""), ((StringConstant) rightValue).value);
                this.machineState = MachineState.PATTERN;
            } else if (rightValue instanceof StaticFieldRef) {
                SootField sootField = ((StaticFieldRef) rightValue).getField();
                if (sootField.getDeclaringClass().resolvingLevel() < SootClass.BODIES) {
                    return;
                }
                List<Unit> units = new ArrayList<>(sootField.getDeclaringClass().getMethodByName("<clinit>").retrieveActiveBody().getUnits());
                for (Unit unit : units) {
                    if (unit instanceof AssignStmt) {
                        AssignStmt aUnit = (AssignStmt) unit;
                        if (aUnit.getRightOp() instanceof StringConstant) {
                            addPatternURL(aUnit.getLeftOp().toString().replaceAll("\\[[^)]*]", ""), ((StringConstant) aUnit.getRightOp()).value);
                        } else if (aUnit.getLeftOp().toString().contains(sootField.toString()) && varAndString.containsKey(aUnit.getRightOp().toString())) {
                            addPatternURL(sootField.toString(), varAndString.get(aUnit.getRightOp().toString()));
                            addPatternURL(leftValue.toString(), varAndString.get(aUnit.getRightOp().toString()));
                            break;
                        }
                    }
                }
            } else if (varAndString.containsKey(rightValue.toString())) {
                addPatternURL(leftValue.toString().replaceAll("\\[[^)]*]", ""), varAndString.get(rightValue.toString()));
                this.machineState = MachineState.PATTERN;
            } else if (rightValue instanceof JInstanceFieldRef) {
                this.localVariable.put(leftValue.toString(), rightValue);
            }
        }
    }

    @Override
    void addPathForMethod(String path, SootMethod method, HandlerModel handlerModel) {
        for (SootMethod interceptorMethod : handlerModel.getSootClass().getMethods()) {
            String methodName = interceptorMethod.getName();
            if (methodName.equals("preHandle") || methodName.equals("postHandle") || methodName.equals("afterCompletion")) {
                HandlerModel configModel = new HandlerModel(this);
                configModel.setSootMethod(interceptorMethod);
                configModel.setSootClass(handlerModel.getSootClass());
                configModel.setPointcutExpressions(handlerModel.getPointcutExpressions());
                configModel.setPointcutExcludes(handlerModel.getPointcutExcludes());
                configModel.setHandlerModelEnum(HandlerModelEnum.INTERCEPTOR);
                configModel.setSuperClassList(configModel.getSuperClassList());
                putConfigToScope(configModel, interceptorMethod);
                savePointMethod(configModel, method.getDeclaringClass(), method);
            }
        }
    }
}
