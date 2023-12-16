package backend;

import bean.*;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import transformer.BasicCoRTransformer;
import transformer.HandlerEnum;
import utils.FactoryUtil;
import utils.JimpleUtil;
import utils.SootUtil;

import java.util.*;

public class FrameworkModelingEngine {
    private final JimpleUtil jimpleUtil = new JimpleUtil();
    private BasicCoRTransformer basicCoRTransformer;

    public void frameworkModeling(HandlerTargetModel handlerTargetModel) {
        Set<GotoEdge> gotoEdgeSet = new HashSet<>();
        SootMethod targetMethod = handlerTargetModel.getSootMethod();
        SootMethod proxyMethod;
        SootMethod preProxyMethod = null;
        SootMethod currentMethod = null;
        SootMethod preMethod = null;
        String analysisName = "";
        GotoEdge gotoEdge = null;

        for (HandlerModel handlerModel : handlerTargetModel.getHandlerChain()) {
            this.basicCoRTransformer = FactoryUtil.getCoRTransformer(handlerModel.getHandlerModelEnum());
            if (!this.basicCoRTransformer.getAnalysisName().equals(analysisName)) {
                ProxyModel proxyModel = handlerTargetModel.getProxyModelMap().get(this.basicCoRTransformer.getAnalysisName());
                proxyMethod = proxyModel.getProxyMethod();
                if (preProxyMethod == null || !preProxyMethod.equals(proxyMethod)) {
                    if (preProxyMethod != null) {
                        insertTargetMethod(currentMethod, proxyMethod);
                    }
                    currentMethod = proxyMethod;
                    this.basicCoRTransformer.modifyJimpleBody(proxyMethod);
                    preMethod = proxyMethod;
                    preProxyMethod = proxyMethod;
                }
                if (analysisName.equals("")) {
                    if (proxyModel.getProxyClass() != null) {
                        BasicCoRTransformer.proxyClassMap.put(handlerTargetModel.getClassName(), proxyModel.getProxyClass());
                    }
                    BasicCoRTransformer.proxyMethodMap.put(handlerTargetModel.getMethodName(), proxyMethod);
                }
                analysisName = this.basicCoRTransformer.getAnalysisName();
            }
            GotoStmtModel tmpModel = null;
            switch (handlerModel.getAnnotation()) {
                case INVOKE:
                    preMethod = currentMethod;
                    SootMethod aroundMethod = aroundParser(handlerModel, targetMethod);
                    insertInvoke(currentMethod, aroundMethod);
                    currentMethod = aroundMethod;
                    break;
                case PRE_CODE:
                    tmpModel = insertPreCode(currentMethod, handlerModel);
                    gotoEdge = this.basicCoRTransformer.gotoLink();
                    break;
                case POST_CODE:
                    SootMethod insertTargetMethod;
                    if (basicCoRTransformer.getVersion()) {
                        insertTargetMethod = currentMethod;
                    } else {
                        insertTargetMethod = preMethod;
                    }
                    tmpModel = insertPostCode(insertTargetMethod, handlerModel);
                    break;
                case COMPLETION:
                    if (basicCoRTransformer.getVersion()) {
                        insertTargetMethod = currentMethod;
                    } else {
                        insertTargetMethod = preMethod;
                    }
                    insertCompletionCode(insertTargetMethod, handlerModel);
                    break;
                case EXCEPTION:
                    break;
            }
            genGotoEdge(tmpModel, gotoEdge, gotoEdgeSet);
        }
        insertTargetMethod(currentMethod, targetMethod);
        insertGotoStmt(gotoEdgeSet);
    }

    private void insertGotoStmt(Set<GotoEdge> gotoEdgeSet) {
        for (GotoEdge edge : gotoEdgeSet) {
            Unit ifUint;
            if (edge.getSinkModel() != null) {
                ifUint = jimpleUtil.createIfWithEq(edge.getSourceModel().getReturnLocal(), edge.getSinkModel().getAssignUnit());
            } else {
                ifUint = jimpleUtil.createIfWithEq(edge.getSourceModel().getReturnLocal(), edge.getSourceModel().getGotoTmpUnit());
            }
            JimpleBody jimpleBody = (JimpleBody) edge.getSourceModel().getInsertedBody();
            jimpleBody.getUnits().insertAfter(ifUint, edge.getSourceModel().getAssignUnit());
        }
    }

    private void genGotoEdge(GotoStmtModel tmpModel, GotoEdge gotoEdge, Set<GotoEdge> gotoEdgeSet) {
        if (tmpModel == null || gotoEdge == null) {
            return;
        }

        if (tmpModel.getAdviceEnum().equals(gotoEdge.getSourceAdvice())) {
            gotoEdge.setSourceModel(tmpModel);
        } else if (tmpModel.getAdviceEnum().equals(gotoEdge.getSinkAdvice())) {
            gotoEdge.setSinkModel(tmpModel);
        }

        if (gotoEdge.getSourceModel() != null) {
            gotoEdgeSet.add(gotoEdge);
        }
    }

    private SootMethod aroundParser(HandlerModel handlerModel, SootMethod targetMethod) {
        List<Type> parameterTypes = new ArrayList<>(handlerModel.getSootMethod().getParameterTypes());
        JimpleBody handlerBody = (JimpleBody) handlerModel.getSootMethod().retrieveActiveBody().clone();
        PatchingChain<Unit> aspectUnits = handlerBody.getUnits();
        int addNumber = 0;
        if (this.basicCoRTransformer.addFormalParamForAround()) {
            for (Type type : targetMethod.getParameterTypes()) {
                if (!handlerModel.getSootMethod().getParameterTypes().contains(type)) {
                    parameterTypes.add(type);
                    addNumber++;
                }
            }
        }

        if (this.basicCoRTransformer.addSelfClassFormalParamForAround()) {
            parameterTypes.add(targetMethod.getDeclaringClass().getType());
            addNumber += 1;
        }

        SootMethod newHandlerMethod = new SootMethod(handlerModel.getSootMethod().getName()
                + "_" + SootUtil.getHashCode(handlerModel.getSootClass().getName()
                + targetMethod.getDeclaringClass().getName()
                + targetMethod.getName() + targetMethod.getParameterTypes()),
                parameterTypes,
                handlerModel.getSootMethod().getReturnType(),
                Modifier.PUBLIC);
        handlerModel.getSootClass().addMethod(newHandlerMethod);

        newHandlerMethod.setActiveBody(handlerBody);
        for (int i = addNumber; 0 < i; i--) {
            Type tmpType = parameterTypes.get(parameterTypes.size() - i);
            Local paramLocal = jimpleUtil.addLocalVar("param" + (parameterTypes.size() - i), tmpType, handlerBody);
            List<Unit> unitList = new ArrayList<>(aspectUnits);
            aspectUnits.insertAfter(
                    jimpleUtil.createIdentityStmt(paramLocal, jimpleUtil.createParamRef(tmpType, parameterTypes.size() - i))
                    , unitList.get(jimpleUtil.getAtStmtNumber(aspectUnits) - 1));
        }
        findSpecialPoint(handlerModel, newHandlerMethod);
        return newHandlerMethod;
    }

    private void findSpecialPoint(HandlerModel handlerModel, SootMethod newHandlerMethod) {
        List<Integer> returnList = new ArrayList<>();
        List<Integer> insertPointList = new ArrayList<>();
        List<Integer> pjpList = new ArrayList<>();
        List<Integer> invokePointList = new ArrayList<>();
        List<SootMethod> candidateSootMethod = new ArrayList<>();
        int lineNumber = 0;
        Type trackLocalType = null;
        PatchingChain<Unit> aspectUnits = newHandlerMethod.retrieveActiveBody().getUnits();
        for (Unit unit : aspectUnits) {
            if ((unit instanceof JReturnVoidStmt) || (unit instanceof JReturnStmt)) {
                returnList.add(lineNumber);
                insertPointList.add(lineNumber);
            } else if (this.basicCoRTransformer.specialPoint(unit)) {
                pjpList.add(lineNumber);
            } else if (isInvokeMethod(unit, handlerModel, trackLocalType)) {
                invokePointList.add(lineNumber);
                SootMethod candidate = ((Stmt) unit).getInvokeExpr().getMethod();
                if (!candidateSootMethod.contains(candidate)) {
                    for (SootClass sootClass : handlerModel.getSuperClassList()) {
                        if (sootClass.getName().equals(candidate.getDeclaringClass().getName())) {
                            break;
                        }
                        if (sootClass.declaresMethod(candidate.getSubSignature())) {
                            SootMethod subCandidate = sootClass.getMethod(candidate.getSubSignature());
                            if (newHandlerMethod.getDeclaringClass() == subCandidate.getDeclaringClass()
                                    && newHandlerMethod.getName().contains(subCandidate.getName() + "_")) {
                                break;
                            }
                            candidate = subCandidate;
                            break;
                        }
                    }
                    candidateSootMethod.add(candidate);
                }
            } else if (((Stmt) unit).containsInvokeExpr()
                    && isSameParam(((Stmt) unit).getInvokeExpr(), handlerModel.getSootMethod())
                    && unit.toString().contains("<init>")) {
                trackLocalType = ((JSpecialInvokeExpr) ((Stmt) unit).getInvokeExpr()).getBase().getType();
            }
            lineNumber++;
        }
        List<SootMethod> removeCandidateSootMethod = new ArrayList<>();
        for (int i = 0; i < candidateSootMethod.size(); i++) {
            SootMethod method = candidateSootMethod.get(i);
            SootMethod invokeMethod = changeInvokeStmt(handlerModel, method, newHandlerMethod, invokePointList);
            if (invokeMethod == null) {
                removeCandidateSootMethod.add(method);
                continue;
            }
            findSpecialPoint(handlerModel, invokeMethod);
            candidateSootMethod.set(i, invokeMethod);
        }
        candidateSootMethod.removeAll(removeCandidateSootMethod);
        BasicCoRTransformer.insertMethodMap.put(newHandlerMethod.getSignature(), new InsertMethod(newHandlerMethod, returnList, insertPointList, pjpList, candidateSootMethod));
    }

    private boolean isInvokeMethod(Unit unit, HandlerModel handlerModel, Type trackLocalType) {
        return ((Stmt) unit).containsInvokeExpr() && !unit.toString().contains("<init>") && (isSameParam(((Stmt) unit).getInvokeExpr(), handlerModel.getSootMethod())
                || (trackLocalType != null && unit.toString().contains(trackLocalType.toString())));
    }

    private boolean isSameParam(InvokeExpr ie, SootMethod flagMethod) {
        if (ie.getArgCount() == 0 || flagMethod.getParameterCount() == 0 || ie.getArgCount() != flagMethod.getParameterCount()
                || ie instanceof StaticInvokeExpr) {
            return false;
        }
        for (Value arg : ie.getArgs()) {
            Type argType = arg.getType();
            if (!flagMethod.getParameterTypes().contains(argType)) {
                if (argType instanceof RefType) {
                    boolean flag = false;
                    for (SootClass anInterface : ((RefType) argType).getSootClass().getInterfaces()) {
                        if (flagMethod.getParameterTypes().contains(anInterface.getType())) {
                            flag = true;
                        }
                    }
                    if (!flag) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }


    private SootMethod changeInvokeStmt(HandlerModel handlerModel, SootMethod invokeMethod, SootMethod newHandlerMethod, List<Integer> invokePointLis) {
        SootMethod oriMethod = invokeMethod;
        if (oriMethod.isAbstract()) {
            oriMethod = getImplementMethod(oriMethod, handlerModel);
            if (oriMethod.isAbstract()) {
                return null;
            }
        }
        SootMethod newInvokeMethod = new SootMethod(oriMethod.getName() + "_" + SootUtil.getHashCode(newHandlerMethod.getName()),
                oriMethod.getParameterTypes(),
                oriMethod.getReturnType(),
                oriMethod.getModifiers());
        newHandlerMethod.getDeclaringClass().addMethod(newInvokeMethod);
        JimpleBody newInvokeBody = (JimpleBody) oriMethod.retrieveActiveBody().clone();
        newInvokeMethod.setActiveBody(newInvokeBody);
        PatchingChain<Unit> handlerUnits = newHandlerMethod.retrieveActiveBody().getUnits();
        List<Unit> aspectUnitList = new ArrayList<>(handlerUnits);

        for (Integer invokePointLi : invokePointLis) {
            Stmt insertPointStmt = (Stmt) aspectUnitList.get(invokePointLi);
            if (insertPointStmt.getInvokeExpr().getMethod().getSubSignature().equals(oriMethod.getSubSignature())) {
                List<Value> args = insertPointStmt.getInvokeExpr().getArgs();
                Unit invokeStmt = jimpleUtil.specialCallStatement(newHandlerMethod.retrieveActiveBody().getThisLocal(), newInvokeMethod, args);
                handlerUnits.insertAfter(invokeStmt, insertPointStmt);
                handlerUnits.remove(insertPointStmt);
            }
        }
        return newInvokeMethod;
    }

    private SootMethod getImplementMethod(SootMethod sootMethod, HandlerModel handlerModel) {
        String methodSig = sootMethod.getSubSignature();
        SootClass handlerClass = handlerModel.getSootClass();
        if (handlerClass.declaresMethod(methodSig)) {
            return handlerClass.getMethod(methodSig);
        } else if (SootUtil.classAndInnerClass.containsKey(handlerClass)) {
            for (SootClass sootClass : SootUtil.classAndInnerClass.get(handlerClass)) {
                if (SootUtil.getAllSuperClasses(sootClass).contains(sootMethod.getDeclaringClass()) && sootClass.declaresMethod(methodSig)) {
                    return sootClass.getMethod(methodSig);
                }
            }
        }
        for (SootClass superClass : handlerModel.getSuperClassList()) {
            if (superClass == sootMethod.getDeclaringClass()) {
                break;
            }
            if (superClass.declaresMethod(methodSig)) {
                return superClass.getMethod(methodSig);
            }
        }
        return sootMethod;
    }

    private void insertInvoke(SootMethod currentMethod, SootMethod calleeMethod) {
        InsertMethod im = BasicCoRTransformer.insertMethodMap.get(currentMethod.getSignature());
        List<Integer> returnList = im.getReturnList();
        List<Integer> insertPointList = im.getPjpList() == null ? im.getInsertPointList() : im.getPjpList();
        List<SootMethod> candidateSootMethods = im.getCandidateSootMethod() == null ? new ArrayList<>() : im.getCandidateSootMethod();

        JimpleBody currentMethodBody = (JimpleBody) currentMethod.retrieveActiveBody();
        PatchingChain<Unit> units = currentMethodBody.getUnits();
        if (insertPointList.size() != 0 || candidateSootMethods.size() != 0) {
            int modifyLineNumber = 0;
            Local localModel = jimpleUtil.newLocalVar(calleeMethod.getDeclaringClass().getShortName().toLowerCase(), calleeMethod.getDeclaringClass().getType());
            int beforeUnitCount = units.size();
            Local returnLocal = jimpleUtil.initLocalModel(currentMethod, calleeMethod, currentMethodBody, units, localModel);
            if (returnLocal != localModel) {
                localModel = returnLocal;
            }

            List<Value> paramList = jimpleUtil.fillParameters(currentMethod, currentMethodBody, calleeMethod);
            modifyLineNumber += (units.size() - beforeUnitCount);
            Local returnRef = null;
            List<Unit> unitList = new LinkedList<>(units);
            for (int i = 0; i < insertPointList.size(); i++) {
                if (!(currentMethod.getReturnType() instanceof VoidType)) {
                    if (returnRef == null) {
                        String returnRefName = unitList.get(returnList.get(i) + modifyLineNumber).toString().replace("return ", "");
                        for (Local local : currentMethodBody.getLocals()) {
                            if (local.getName().equals(returnRefName)) {
                                returnRef = local;
                                break;
                            }
                        }
                        if (returnRef == null) {
                            returnRef = jimpleUtil.newLocalVar(returnRefName, currentMethod.getReturnType());
                        }
                    }

                    Value returnValue = jimpleUtil.createVirtualInvokeExpr(localModel, calleeMethod, paramList);
                    if (im.getPjpList() != null) {
                        units.insertAfter(jimpleUtil.createAssignStmt(returnRef, returnValue), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    } else {
                        units.insertBefore(jimpleUtil.createAssignStmt(returnRef, returnValue), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    }
                } else {
                    if (im.getPjpList() != null) {
                        units.insertAfter(jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    } else {
                        units.insertBefore(jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    }
                }
                modifyLineNumber += 1;
                for (int j = 0; j < returnList.size(); j++) {
                    returnList.set(j, returnList.get(j) + modifyLineNumber);
                }
                insertPointList.set(i, insertPointList.get(i) + modifyLineNumber);
                unitList = new LinkedList<>(units);
            }
            for (SootMethod candidateSootMethod : candidateSootMethods) {
                insertInvoke(candidateSootMethod, calleeMethod);
            }
        }
    }

    private void insertTargetMethod(SootMethod currentMethod, SootMethod calleeMethod) {
        int modifyLineNumber = 0;
        InsertMethod im = BasicCoRTransformer.insertMethodMap.get(currentMethod.toString());
        List<Integer> returnList = im.getReturnList();
        List<Integer> insertPointList = im.getPjpList() == null ? im.getInsertPointList() : im.getPjpList();
        List<SootMethod> candidateSootMethods = im.getCandidateSootMethod() == null ? new ArrayList<>() : im.getCandidateSootMethod();

        if ((im.getPjpList() != null && insertPointList.size() != 0) || candidateSootMethods.size() == 0) {
            JimpleBody body = (JimpleBody) currentMethod.retrieveActiveBody();
            PatchingChain<Unit> units = body.getUnits();
            int beforeUnitCount = units.size();
            Local localModel = null;
            Local tmpLocal = jimpleUtil.checkLocalExist(body, calleeMethod.getDeclaringClass().getType());
            if (currentMethod.getDeclaringClass().getSuperclass().equals(calleeMethod.getDeclaringClass())) {
                for (Local local : body.getLocals()) {
                    if (local.getName().equals("localTarget")) {
                        localModel = local;
                        break;
                    }
                }
            } else if (tmpLocal != null) {
                localModel = tmpLocal;
            } else {
                Local newLocal = jimpleUtil.newLocalVar(calleeMethod.getDeclaringClass().getShortName(), calleeMethod.getDeclaringClass().getType());
                localModel = jimpleUtil.initLocalModel(currentMethod, calleeMethod, body, units, newLocal);
            }

            List<Value> paramList = jimpleUtil.fillParameters(currentMethod, body, calleeMethod);
            modifyLineNumber += (units.size() - beforeUnitCount);
            List<Unit> unitList = new LinkedList<>(units);
            Local returnRef = null;
            for (int i = 0; i < insertPointList.size(); i++) {
                if (!(currentMethod.getReturnType() instanceof VoidType) && !(calleeMethod.getReturnType() instanceof VoidType) && !jimpleUtil.isBaseTypes(calleeMethod.getReturnType())) {
                    if (returnRef == null) {
                        String returnRefName = unitList.get(returnList.get(i) + modifyLineNumber).toString().replace("return ", "");
                        for (Local local : body.getLocals()) {
                            if (local.getName().equals(returnRefName)) {
                                returnRef = local;
                                break;
                            }
                        }
                        if (returnRef == null) {
                            returnRef = jimpleUtil.newLocalVar(returnRefName, RefType.v(currentMethod.getReturnType().toString()));
                        }
                    }

                    Value returnValue = jimpleUtil.createVirtualInvokeExpr(localModel, calleeMethod, paramList);
                    if (im.getPjpList() != null) {
                        units.insertAfter(jimpleUtil.createAssignStmt(returnRef, returnValue), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    } else {
                        units.insertBefore(jimpleUtil.createAssignStmt(returnRef, returnValue), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    }
                } else {
                    if (im.getPjpList() != null) {
                        units.insertAfter(jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    } else {
                        units.insertBefore(jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                    }
                }
                modifyLineNumber += 1;
                for (int j = 0; j < returnList.size(); j++) {
                    returnList.set(j, returnList.get(j) + modifyLineNumber);
                }
                insertPointList.set(i, insertPointList.get(i) + modifyLineNumber);
                unitList = new LinkedList<>(units);
            }
        }
        for (SootMethod candidateSootMethod : candidateSootMethods) {
            insertTargetMethod(candidateSootMethod, calleeMethod);
        }
    }

    private GotoStmtModel insertPreCode(SootMethod currentMethod, HandlerModel aspectModel) {
        GotoStmtModel gotoStmtModel = new GotoStmtModel();
        SootMethod calleeMethod = aspectModel.getSootMethod();
        int modifyLineNumber = 0;
        Local localModel = jimpleUtil.newLocalVar(calleeMethod.getDeclaringClass().getShortName().toLowerCase(), calleeMethod.getDeclaringClass().getType());
        JimpleBody currentMethodBody = (JimpleBody) currentMethod.retrieveActiveBody();
        PatchingChain<Unit> units = currentMethodBody.getUnits();
        int beforeUnitCount = units.size();
        Local returnLocal = jimpleUtil.initLocalModel(currentMethod, calleeMethod, currentMethodBody, units, localModel);
        if (returnLocal != localModel) {
            localModel = returnLocal;
        }

        List<Value> paramList = jimpleUtil.fillParameters(currentMethod, currentMethodBody, calleeMethod);
        modifyLineNumber += (units.size() - beforeUnitCount);
        InsertMethod im = BasicCoRTransformer.insertMethodMap.get(currentMethod.toString());
        List<Integer> returnList = im.getReturnList();
        List<Integer> insertPointList = im.getPjpList() == null ? im.getInsertPointList() : im.getPjpList();
        List<Unit> unitList = new LinkedList<>(units);
        Local invokeReturnRef;

        for (int i = 0; i < insertPointList.size(); i++) {
            if (localModel != currentMethodBody.getThisLocal()) {
                if (!(calleeMethod.getReturnType() instanceof VoidType)) {
                    invokeReturnRef = jimpleUtil.addLocalVar("returnRef" + aspectModel.getSootClass().getShortName() + calleeMethod.getName(), calleeMethod.getReturnType(), currentMethodBody);
                    Unit assign = jimpleUtil.createAssignStmt(invokeReturnRef, jimpleUtil.createVirtualInvokeExpr(localModel, calleeMethod, paramList));
                    units.insertBefore(assign, unitList.get(insertPointList.get(i) + modifyLineNumber));
                    gotoStmtModel.setReturnLocal(invokeReturnRef);
                    gotoStmtModel.setAssignUnit(assign);
                    gotoStmtModel.setInsertedBody(currentMethodBody);
                    gotoStmtModel.setAdviceEnum(HandlerEnum.PRE_CODE);
                    gotoStmtModel.setGotoTmpUnit(unitList.get(insertPointList.get(i) + modifyLineNumber));
                } else {
                    units.insertBefore(jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                }
            } else {
                units.insertBefore(jimpleUtil.specialCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
            }
            modifyLineNumber += 1;
            for (int j = 0; j < returnList.size(); j++) {
                returnList.set(j, returnList.get(j) + modifyLineNumber);
            }
            insertPointList.set(i, insertPointList.get(i) + modifyLineNumber);
            unitList = new LinkedList<>(units);
        }

        return gotoStmtModel;
    }

    private void insertCompletionCode(SootMethod currentMethod, HandlerModel handlerModel) {
        SootMethod calleeMethod = handlerModel.getSootMethod();
        int modifyLineNumber = 0;
        Local localModel = jimpleUtil.newLocalVar(calleeMethod.getDeclaringClass().getShortName().toLowerCase(), calleeMethod.getDeclaringClass().getType());
        JimpleBody currentMethodBody = (JimpleBody) currentMethod.retrieveActiveBody();
        PatchingChain<Unit> units = currentMethodBody.getUnits();
        int beforeUnitCount = units.size();

        Local returnLocal = jimpleUtil.initLocalModel(currentMethod, calleeMethod, currentMethodBody, units, localModel);
        if (returnLocal != localModel) {
            localModel = returnLocal;
        }

        List<Value> paramList = jimpleUtil.fillParameters(currentMethod, currentMethodBody, calleeMethod);
        modifyLineNumber += (units.size() - beforeUnitCount);
        InsertMethod im = BasicCoRTransformer.insertMethodMap.get(currentMethod.toString());
        List<Integer> returnList = im.getReturnList();
        List<Integer> insertPointList = (this.basicCoRTransformer.getVersion() && im.getPjpList() != null) ? im.getPjpList() : im.getInsertPointList();
        List<Unit> unitList = new LinkedList<>(units);
        for (int i = 0; i < insertPointList.size(); i++) {
            if (!this.basicCoRTransformer.getVersion()) {
                if (localModel != currentMethodBody.getThisLocal()) {
                    units.insertBefore(jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                } else {
                    units.insertBefore(jimpleUtil.specialCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
                }
            } else {
                units.insertAfter(jimpleUtil.specialCallStatement(localModel, calleeMethod, paramList), unitList.get(insertPointList.get(i) + modifyLineNumber));
            }

            modifyLineNumber += 1;
            for (int j = 0; j < returnList.size(); j++) {
                returnList.set(j, returnList.get(j) + modifyLineNumber);
            }
            insertPointList.set(i, insertPointList.get(i) + modifyLineNumber - 1);
            unitList = new LinkedList<>(units);
        }
    }

    private GotoStmtModel insertPostCode(SootMethod currentMethod, HandlerModel handlerModel) {
        GotoStmtModel gotoStmtModel = new GotoStmtModel();
        SootMethod calleeMethod = handlerModel.getSootMethod();
        int modifyLineNumber = 0;
        Local localModel = jimpleUtil.newLocalVar(calleeMethod.getDeclaringClass().getShortName().toLowerCase(), calleeMethod.getDeclaringClass().getType());
        JimpleBody currentMethodBody = (JimpleBody) currentMethod.retrieveActiveBody();
        PatchingChain<Unit> units = currentMethodBody.getUnits();
        int beforeUnitCount = units.size();
        Local returnLocal = jimpleUtil.initLocalModel(currentMethod, calleeMethod, currentMethodBody, units, localModel);
        if (returnLocal != localModel) {
            localModel = returnLocal;
        }

        List<Value> paramList = jimpleUtil.fillParameters(currentMethod, currentMethodBody, calleeMethod);
        modifyLineNumber += (units.size() - beforeUnitCount);
        InsertMethod im = BasicCoRTransformer.insertMethodMap.get(currentMethod.toString());
        List<Integer> returnList = im.getReturnList();
        List<Integer> insertPointList =
                (this.basicCoRTransformer.getVersion() && im.getPjpList() != null) ? im.getPjpList() : im.getInsertPointList();
        List<Unit> unitList = new LinkedList<>(units);
        for (int i = 0; i < insertPointList.size(); i++) {
            Unit targetUnit;
            if (!this.basicCoRTransformer.getVersion()) {
                if (localModel != currentMethodBody.getThisLocal()) {
                    targetUnit = jimpleUtil.virtualCallStatement(localModel, calleeMethod, paramList);
                    units.insertBefore(targetUnit, unitList.get(insertPointList.get(i) + modifyLineNumber));
                } else {
                    targetUnit = jimpleUtil.specialCallStatement(localModel, calleeMethod, paramList);
                    units.insertBefore(targetUnit, unitList.get(insertPointList.get(i) + modifyLineNumber));
                }
            } else {
                targetUnit = jimpleUtil.specialCallStatement(localModel, calleeMethod, paramList);
                units.insertAfter(targetUnit, unitList.get(insertPointList.get(i) + modifyLineNumber));
            }
            gotoStmtModel.setAssignUnit(targetUnit);
            gotoStmtModel.setInsertedBody(currentMethodBody);
            gotoStmtModel.setAdviceEnum(HandlerEnum.POST_CODE);
            modifyLineNumber += 1;

            for (int j = 0; j < returnList.size(); j++) {
                returnList.set(j, returnList.get(j) + modifyLineNumber);
            }
            insertPointList.set(i, insertPointList.get(i) + modifyLineNumber - 1);
            unitList = new LinkedList<>(units);
        }
        return gotoStmtModel;
    }
}
