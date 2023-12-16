package utils;

import soot.*;
import soot.jimple.*;

import java.util.List;

public class BaseJimpleUtil {
    public Local newLocalVar(String localName, String vtype) {
        return Jimple.v().newLocal(localName, RefType.v(vtype));
    }

    public Local newLocalVar(String localName, Type vtype) {
        return Jimple.v().newLocal(localName, vtype);
    }

    public Local addLocalVar(String localName, String vtype, Body body) {
        Local local = newLocalVar(localName, vtype);
        Local existLocal = checkLocalExist(body, local.getName());
        if (existLocal == null) {
            body.getLocals().add(local);
            return local;
        }
        return existLocal;
    }

    public Local checkLocalExist(Body body, String localName) {
        for (Local local : body.getLocals()) {
            if (local.getName().equals(localName)) {
                return local;
            }
        }
        return null;
    }

    public Local checkLocalExit(Body body, Local local) {
        for (Local existLocal : body.getLocals()) {
            if (existLocal.getName().equals(local.getName())) {
                return existLocal;
            }

            if (existLocal.getType().equals(local.getType())) {
                return existLocal;
            } else if ((existLocal.getType() instanceof RefType && local.getType() instanceof  RefType)
                    && compareInterface(((RefType) existLocal.getType()).getSootClass(), ((RefType) local.getType()).getSootClass())) {
                return existLocal;
            }

        }
        return null;
    }

    private boolean compareInterface(SootClass sootClass1, SootClass sootClass2) {
        if (sootClass1.getInterfaces().size() == 0 && sootClass2.getInterfaces().contains(sootClass1)) {
            return true;
        } else {
            return sootClass2.getInterfaces().size() == 0 && sootClass1.getInterfaces().contains(sootClass2);
        }
    }

    public SootField checkFieldExist(SootClass sootClass, String fieldName) {
        for (SootField field : sootClass.getFields()) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public Local checkLocalExist(Body body, Type type) {
        for (Local local : body.getLocals()) {
            if (local.getType().equals(type)) {
                return local;
            }
        }
        return null;
    }

    public Local addLocalVar(String localName, Type vtype, Body body) {
        Local local = newLocalVar(localName, vtype);
        Local existLocal = checkLocalExist(body, local.getName());
        if (existLocal == null) {
            body.getLocals().add(local);
            return local;
        }
        return existLocal;
    }

    public Unit createAssignStmt(Local local, String realType) {
        return Jimple.v().newAssignStmt(local, Jimple.v().newNewExpr(RefType.v(realType)));
    }

    public void createAssignStmt(Local local, String realType, PatchingChain<Unit> units) {
        units.add(Jimple.v().newAssignStmt(local, Jimple.v().newNewExpr(RefType.v(realType))));
    }

    public Unit createAssignStmt(Value var, Value realvar) {
        return Jimple.v().newAssignStmt(var, realvar);
    }


    public void createAssignStmt(Value left, Value right, PatchingChain<Unit> units) {
        units.add(Jimple.v().newAssignStmt(left, right));
    }

    public NewExpr createNewExpr(String declType) {
        return Jimple.v().newNewExpr(RefType.v(declType));
    }

    public NewExpr createNewExpr(RefType declType) {
        return Jimple.v().newNewExpr(declType);
    }

    public NewArrayExpr createNewArrayExpr(String type, int paramSize) {
        return Jimple.v().newNewArrayExpr(RefType.v(type), IntConstant.v(paramSize));
    }

    public SpecialInvokeExpr createSpecialInvokeExpr(Local localModel, SootMethod calleeMethod) {
        return Jimple.v().newSpecialInvokeExpr(localModel, calleeMethod.makeRef());
    }

    public SpecialInvokeExpr createSpecialInvokeExpr(Local localModel, SootMethod calleeMethod, List<? extends Value> values) {
        return Jimple.v().newSpecialInvokeExpr(localModel, calleeMethod.makeRef(), values);
    }

    public Unit specialCallStatement(Local localModel, String methodSign) {
        SootMethod toCall = Scene.v().getMethod(methodSign);
        return Jimple.v().newInvokeStmt(createSpecialInvokeExpr(localModel, toCall));
    }

    public Unit specialCallStatement(Local localModel, SootMethod calleeMethod) {
        return Jimple.v().newInvokeStmt(createSpecialInvokeExpr(localModel, calleeMethod));
    }

    public Unit specialCallStatement(Local localModel, SootMethod calleeMethod, List<Value> values) {
        return Jimple.v().newInvokeStmt(createSpecialInvokeExpr(localModel, calleeMethod, values));
    }


    public Unit specialCallStatement(Local localModel, String methodSign, List<Value> values) {
        SootMethod toCall = Scene.v().getMethod(methodSign);
        return Jimple.v().newInvokeStmt(createSpecialInvokeExpr(localModel, toCall, values));
    }


    public VirtualInvokeExpr createVirtualInvokeExpr(Local localModel, SootMethod calleeMethod) {
        return Jimple.v().newVirtualInvokeExpr(localModel, calleeMethod.makeRef());
    }

    public VirtualInvokeExpr createVirtualInvokeExpr(Local localModel, SootMethod calleeMethod, List<? extends Value> values) {
        return Jimple.v().newVirtualInvokeExpr(localModel, calleeMethod.makeRef(), values);
    }

    public Unit virtualCallStatement(Local localModel, String methodSign) {
        SootMethod toCall = Scene.v().getMethod(methodSign);
        return Jimple.v().newInvokeStmt(createVirtualInvokeExpr(localModel, toCall));
    }

    public Unit virtualCallStatement(Local localModel, String methodSign, List<? extends Value> values) {
        SootMethod toCall = Scene.v().getMethod(methodSign);
        return Jimple.v().newInvokeStmt(createVirtualInvokeExpr(localModel, toCall, values));
    }

    public Unit virtualCallStatement(Local localModel, SootMethod calleeMethod) {
        return Jimple.v().newInvokeStmt(createVirtualInvokeExpr(localModel, calleeMethod));
    }

    public Unit virtualCallStatement(Local localModel, SootMethod calleeMethod, List<? extends Value> values) {
        return Jimple.v().newInvokeStmt(createVirtualInvokeExpr(localModel, calleeMethod, values));
    }

    public InterfaceInvokeExpr createInterfaceInvokeExpr(Local localModel, SootMethod calleeMethod) {
        return Jimple.v().newInterfaceInvokeExpr(localModel, calleeMethod.makeRef());
    }

    public InterfaceInvokeExpr createInterfaceInvokeExpr(Local localModel, SootMethod calleeMethod, List<? extends Value> values) {
        return Jimple.v().newInterfaceInvokeExpr(localModel, calleeMethod.makeRef(), values);
    }

    public Unit interfaceCallStatement(Local localModel, SootMethod calleeMethod) {
        return Jimple.v().newInvokeStmt(createInterfaceInvokeExpr(localModel, calleeMethod));
    }

    public Unit interfaceCallStatement(Local localModel, SootMethod calleeMethod, List<? extends Value> values) {
        return Jimple.v().newInvokeStmt(createInterfaceInvokeExpr(localModel, calleeMethod, values));
    }

    public StaticInvokeExpr createStaticInvokeExpr(SootMethod calleeMethod) {
        return Jimple.v().newStaticInvokeExpr(calleeMethod.makeRef());
    }

    public StaticInvokeExpr createStaticInvokeExpr(SootMethod calleeMethod, Value arg) {
        return Jimple.v().newStaticInvokeExpr(calleeMethod.makeRef(), arg);
    }

    public StaticInvokeExpr createStaticInvokeExpr(SootMethod calleeMethod, List<Value> args) {
        return Jimple.v().newStaticInvokeExpr(calleeMethod.makeRef(), args);
    }

    public Unit staticCallStatement(SootMethod calleeMethod) {
        return Jimple.v().newInvokeStmt(createStaticInvokeExpr(calleeMethod));
    }

    public Unit staticCallStatement(SootMethod calleeMethod, Value arg) {
        return Jimple.v().newInvokeStmt(createStaticInvokeExpr(calleeMethod, arg));
    }

    public Unit staticCallAndAssignStatement(Local local, SootMethod calleeMethod) {
        return Jimple.v().newAssignStmt(local, createStaticInvokeExpr(calleeMethod));
    }

    public Unit staticCallAndAssignStatement(Local local, SootMethod calleeMethod, Value arg) {
        return Jimple.v().newAssignStmt(local, createStaticInvokeExpr(calleeMethod, arg));
    }

    public Unit createIdentityStmt(Value var, Value identvar) {
        return Jimple.v().newIdentityStmt(var, identvar);
    }

    public void createIdentityStmt(Value var, Value identvar, PatchingChain<Unit> units) {
        units.add(createIdentityStmt(var, identvar));
    }

    public ParameterRef createParamRef(Type type, int index) {
        return Jimple.v().newParameterRef(type, index);
    }

    public ThisRef createThisRef(String type) {
        return createThisRef(RefType.v(type));
    }

    public ThisRef createThisRef(RefType type) {
        return Jimple.v().newThisRef(type);
    }

    public ArrayRef createArrayRef(Value type, int index) {
        return Jimple.v().newArrayRef(type, IntConstant.v(index));
    }

    public void addVoidReturnStmt(PatchingChain<Unit> units) {
        units.add(Jimple.v().newReturnVoidStmt());
    }

    public void addCommonReturnStmt(Value returnRef, PatchingChain<Unit> units) {
        units.add(Jimple.v().newReturnStmt(returnRef));
    }

    public InstanceFieldRef createInstanceFieldRef(Value local, SootFieldRef sootFieldRef) {
        return Jimple.v().newInstanceFieldRef(local, sootFieldRef);
    }


    public StaticFieldRef createStaticFieldRef(SootFieldRef sootFieldRef) {
        return Jimple.v().newStaticFieldRef(sootFieldRef);
    }

    public JimpleBody newMethodBody(SootMethod sootMethod) {
        return Jimple.v().newBody(sootMethod);
    }

    public Unit createIfWithEq(Local ref, Unit target) {
        EqExpr cond = Jimple.v().newEqExpr(ref, IntConstant.v(0));
        return Jimple.v().newIfStmt(cond, target);
    }

    public Unit createIfWithEq(Local ref, Value right, Unit target) {
        EqExpr cond = Jimple.v().newEqExpr(ref, right);
        return Jimple.v().newIfStmt(cond, target);
    }

    public Unit createIfWithNe(Local ref, Value right, Unit target) {
        NeExpr cond = Jimple.v().newNeExpr(ref, right);
        return Jimple.v().newIfStmt(cond, target);
    }

    public CastExpr newCaseExpr(Value value, Type type) {
        return Jimple.v().newCastExpr(value, type);
    }

    public Unit caseAssignStmt(Value left, Value right, Type type) {
        return Jimple.v().newAssignStmt(left, newCaseExpr(right, type));
    }
}
