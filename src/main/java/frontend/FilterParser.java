package frontend;

import bean.HandlerModel;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import transformer.HandlerEnum;
import transformer.HandlerModelEnum;
import transformer.MachineState;
import utils.SootUtil;
import utils.XMLDocumentHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static analysis.CreateEdge.beansAndMethods;
import static utils.StringConstantUtil.FILTER_REGISTRATION_CLASS;

public class FilterParser extends CoRParser {
    private final SootClass filterInterface = Scene.v().getSootClass("javax.servlet.Filter");

    @Override
    public boolean matchConfigMethod(SootMethod method) {
        return SootUtil.matchType(method.getReturnType(), "org.springframework.boot.web.servlet.FilterRegistrationBean")
                || (method.getReturnType() instanceof RefType && SootUtil.getSuperClassesAndInterfaces(((RefType) method.getReturnType()).getSootClass()).contains(filterInterface));
    }

    @Override
    public void caseStart(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.matchClassAndMethod(method, FILTER_REGISTRATION_CLASS, "setFilter")
                    || (SootUtil.matchClassAndMethod(method, FILTER_REGISTRATION_CLASS, "<init>") && method.getParameterCount() > 0)) {
                RefType argType = (RefType) stmt.getInvokeExpr().getArg(0).getType();
                SootClass returnClass;
                if (beansAndMethods.containsKey(argType.getSootClass())) {
                    returnClass = findReturnConcreteClass(beansAndMethods.get(argType.getSootClass()));
                } else {
                    returnClass = argType.getSootClass();
                }
                this.handlerModel = new HandlerModel(returnClass, Integer.MAX_VALUE, this, HandlerModelEnum.FILTER);
                handlerMap.put(returnClass, this.handlerModel);
                this.machineState = MachineState.PATTERN;
            }
        } else if (stmt instanceof JAssignStmt && ((JAssignStmt) stmt).getRightOp() instanceof StringConstant) {
            casePattern(stmt);
        } else if (stmt instanceof JReturnStmt) {
            SootClass returnClass = ((RefType) ((JReturnStmt) stmt).getOpBox().getValue().getType()).getSootClass();
            if (this.handlerModel == null && SootUtil.getSuperClassesAndInterfaces(returnClass).contains(filterInterface) && returnClass != filterInterface) {
                int orderValue = Integer.MAX_VALUE;
                if (returnClass.declaresFieldByName("order")) {
                    SootField orderField = returnClass.getFieldByName("order");
                    List<Unit> units = new ArrayList<>(orderField.getDeclaringClass().getMethodByName("<init>").retrieveActiveBody().getUnits());
                    for (Unit unit : units) {
                        if (unit instanceof AssignStmt && unit.toString().contains(orderField.toString())) {
                            orderValue = ((IntConstant) ((AssignStmt) unit).getRightOp()).value;
                            break;
                        }
                    }
                }
                this.handlerModel = new HandlerModel(returnClass, orderValue, this, HandlerModelEnum.FILTER);
                if (!handlerMap.containsKey(returnClass)) {
                    handlerMap.put(returnClass, this.handlerModel);
                }
            }
        }
    }


    @Override
    public void caseFoundHandler(Stmt stmt) {
        if (stmt instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) stmt;
            if (assignStmt.getRightOp() instanceof JNewExpr) {
                SootClass sootClass = ((JNewExpr) assignStmt.getRightOp()).getBaseType().getSootClass();
                for (SootClass interfaceClass : sootClass.getInterfaces()) {
                    if (SootUtil.matchClass(interfaceClass, "javax.servlet.Filter")) {
                        this.handlerModel.setSootClass(sootClass);
                        this.machineState = MachineState.PATTERN;
                        break;
                    }
                }
            } else if (assignStmt.getRightOp().getType() instanceof ArrayType) {
                this.machineState = MachineState.PATTERN;
            } else if (stmt.containsInvokeExpr() && SootUtil.matchClass(stmt.getInvokeExpr().getMethod().getDeclaringClass(), "org.springframework.security.config.annotation.web.builders.HttpSecurity")) {
                caseStart(stmt);
            }
        }
    }

    @Override
    public void casePattern(Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            SootMethod method = stmt.getInvokeExpr().getMethod();
            if (SootUtil.matchClassAndMethod(method, "org.springframework.boot.web.servlet.AbstractFilterRegistrationBean", "addUrlPatterns")) {
                String zeroArgName = stmt.getInvokeExpr().getArg(0).toString();
                if (varAndString.containsKey(zeroArgName)) {
                    this.handlerModel.addPointcutExpressions(varAndString.get(zeroArgName));
                }
                this.machineState = MachineState.START;
            } else if (method.getName().equals("addInitParameter") && ((StringConstant) stmt.getInvokeExpr().getArg(0)).value.equals("exclusions")) {
                this.handlerModel.addPointcutExclude(varAndString.get(stmt.getInvokeExpr().getArg(1).toString()));
                this.machineState = MachineState.START;
            } else if (SootUtil.matchClassAndMethod(method, FILTER_REGISTRATION_CLASS, "setFilter")
                    || (SootUtil.matchClassAndMethod(method, FILTER_REGISTRATION_CLASS, "<init>") && method.getParameterCount() > 0)) {
                caseStart(stmt);
            } else if (method.getName().equals("setOrder")) {
                Value argValue = stmt.getInvokeExpr().getArg(0);
                if (argValue instanceof JimpleLocal) {
                    List<Unit> unitList = new ArrayList<>(this.targetHandlerMethod.retrieveActiveBody().getUnits());
                    for (int i = 1; i < this.stmtSite; i++) {
                        Stmt assignStmt = (Stmt) unitList.get(this.stmtSite - i);
                        if (assignStmt.containsInvokeExpr()) {
                            argValue = getOrder(assignStmt.getInvokeExpr().getMethod());
                            if (argValue.toString().startsWith("$")) {
                                this.handlerModel.setOrder(Integer.MAX_VALUE);
                            } else {
                                this.handlerModel.setOrder(Integer.parseInt(argValue.toString()));
                            }
                            break;
                        }
                    }
                }
            } else if (stmt instanceof AssignStmt && SootUtil.matchClass(method.getDeclaringClass(), "java.lang.String")) {
                AssignStmt aStmt = (AssignStmt) stmt;
                Value leftValue = aStmt.getLeftOp();
                JVirtualInvokeExpr jvi = (JVirtualInvokeExpr) stmt.getInvokeExpr();
                if (varAndString.containsKey(jvi.getBaseBox().getValue().toString()) && method.getName().contains("replace")) {
                    String regex = ((StringConstant) jvi.getArg(0)).value;
                    String replacement = ((StringConstant) jvi.getArg(1)).value;
                    for (String path : varAndString.get(jvi.getBaseBox().getValue().toString())) {
                        addPatternURL(leftValue.toString(), path.replaceAll(regex, replacement));
                    }
                }
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
                if (sootField.getDeclaringClass().resolvingLevel() < SootClass.BODIES || !sootField.getDeclaringClass().declaresFieldByName("<clinit>")) {
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
            }
        }
    }


    // else if (aStmt.getRightOp() instanceof JimpleLocal) {
    //     this.componentModel.addPointcutExpressions("varLocal: " + ((JimpleLocal) aStmt.getRightOp()).getName());
    // }

    @Override
    void addPathForMethod(String path, SootMethod method, HandlerModel handlerModel) {
        if (handlerModel.getSootClass() == null) {
            return;
        }
        if (handlerModel.getSootMethod() == null) {
            SootMethod doFilter = getMethodFor_doFilter(handlerModel.getSootClass());
            if (doFilter.isAbstract()) {
                return;
            }
            handlerModel.setSootMethod(doFilter);
            handlerModel.setAnnotation(HandlerEnum.INVOKE);
        }
        if (handlerModel.getSootMethod() != null) {
            savePointMethod(handlerModel, method.getDeclaringClass(), method);
        }
    }

    private SootMethod getMethodFor_doFilter(SootClass sootClass) {
        if (sootClass == null) {
            return null;
        }
        List<Type> param = new ArrayList<>();
        param.add(RefType.v("javax.servlet.ServletRequest"));
        param.add(RefType.v("javax.servlet.ServletResponse"));
        param.add(RefType.v("javax.servlet.FilterChain"));
        if (sootClass.declaresMethod("doFilter", param)) {
            return sootClass.getMethod("doFilter", param);
        } else {
            return getMethodFor_doFilter(sootClass.getSuperclass());
        }
    }


    public void getXMLFilterSootClazzes(Set<String> xmlpaths) {
        XMLDocumentHolder holder = getXMLHolder(xmlpaths);
        if (holder == null) {
            return;
        }
        Map<String, String> allFilterMap = holder.getFilterMap();
        for (String key : allFilterMap.keySet()) {
            HandlerModel handlerModel = new HandlerModel(Scene.v().getSootClass(key), Integer.MAX_VALUE, new FilterParser(), HandlerModelEnum.FILTER);
            handlerModel.addPointcutExpressions(allFilterMap.get(key));
            handlerMap.put(handlerModel.getSootClass(), handlerModel);
        }
    }
}
