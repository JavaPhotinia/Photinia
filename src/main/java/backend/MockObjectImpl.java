package backend;

import soot.*;
import soot.jimple.JimpleBody;
import utils.JimpleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static analysis.CreateEdge.projectMainMethod;
import static utils.StringConstantUtil.OBJECT_CLASS;

public class MockObjectImpl implements MockObject {
    private final JimpleUtil jimpleUtil = new JimpleUtil();
    private final GenerateSyntheticClass gsc = new GenerateSyntheticClassImpl();
    @Override
    public void mockJoinPoint(JimpleBody body, PatchingChain<Unit> units) {
        SootClass abstractClass = Scene.v().getSootClass("org.aspectj.lang.ProceedingJoinPoint");
        SootClass joinPointImpl = gsc.generateJoinPointImpl(abstractClass);
        Local joinPointLocal = jimpleUtil.addLocalVar(joinPointImpl.getShortName(), joinPointImpl.getName(), body);
        Local paramArray = jimpleUtil.addLocalVar("paramArray",
                ArrayType.v(RefType.v(OBJECT_CLASS), 1),
                body);

        int paramSize = body.getParameterLocals().size();
        jimpleUtil.createAssignStmt(joinPointLocal, joinPointImpl.getName(), units);
        Unit specialInit = jimpleUtil.specialCallStatement(joinPointLocal,
                joinPointImpl.getMethodByName("<init>").toString());
        units.add(specialInit);
        jimpleUtil.createAssignStmt(paramArray,
                jimpleUtil.createNewArrayExpr(OBJECT_CLASS, paramSize), units);
        for (int i = 0; i < paramSize; i++) {
            jimpleUtil.createAssignStmt(jimpleUtil.createArrayRef(paramArray, i), body.getParameterLocal(i), units);
        }
        units.add(jimpleUtil.virtualCallStatement(joinPointLocal,
                joinPointImpl.getMethodByName("setArgs_synthetic").toString(), Collections.singletonList(paramArray)));
    }

    @Override
    public Local mockBean(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass, SootMethod toCall) {
        String methodName = "get" + sootClass.getShortName() + "Instance";
        Local paramRef = jimpleUtil.newLocalVar(toCall.getName().replace("<init>", "init")
                + sootClass.getShortName(), sootClass.getName());
        Local existLocal = jimpleUtil.checkLocalExit(body, paramRef);
        if (existLocal != null) {
            return existLocal;
        }
        body.getLocals().add(paramRef);
        if (body.getMethod().getDeclaringClass().declaresMethod(methodName, new ArrayList<>(), sootClass.getType())) {
            SootMethod getMockBean = body.getMethod().getDeclaringClass().getMethod(methodName, new ArrayList<>(), sootClass.getType());
            List<Unit> unitList = new ArrayList<>(units);
            units.insertAfter(jimpleUtil.createAssignStmt(paramRef, jimpleUtil.createSpecialInvokeExpr(body.getThisLocal(), getMockBean)),
                    unitList.get(jimpleUtil.getAtStmtNumber(units) - 1));
            return paramRef;
        }
        List<SootMethod> signatures = new ArrayList<>();
        for (SootMethod beanMethod : sootClass.getMethods()) {
            if (beanMethod.getName().startsWith("set") && beanMethod.getParameterTypes().size() > 0) {
                signatures.add(beanMethod);
            }
        }
        SootMethod customMethod = jimpleUtil.genCustomMethodWithCall(body.getMethod().getDeclaringClass(),
                methodName,
                new ArrayList<>(),
                sootClass.getType(),
                signatures);
        body.getMethod().getDeclaringClass().addMethod(customMethod);
        Unit assignStmt;
        if (body.getMethod() != projectMainMethod) {
            assignStmt = jimpleUtil.createAssignStmt(paramRef, jimpleUtil.createVirtualInvokeExpr(body.getThisLocal(), customMethod));
        } else {
            customMethod.setModifiers(Modifier.STATIC + Modifier.PUBLIC);
            assignStmt = jimpleUtil.staticCallAndAssignStatement(paramRef, customMethod);
        }
        List<Unit> unitList = new ArrayList<>(units);
        units.insertAfter(assignStmt, unitList.get(jimpleUtil.getAtStmtNumber(units) - 1));
        return paramRef;
    }

    @Override
    public Local mockHttpServlet(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        Local paramRef = jimpleUtil.newLocalVar(sootClass.getShortName().toLowerCase(), sootClass.getName());
        Local existLocal = jimpleUtil.checkLocalExit(body, paramRef);
        if (existLocal != null) {
            return existLocal;
        }
        body.getLocals().add(paramRef);
        SootClass HttpServletImpl = gsc.generateHttpServlet(sootClass);
        List<Unit> unitList = new ArrayList<>(units);
        Unit assign = jimpleUtil.createAssignStmt(paramRef, HttpServletImpl.getName());
        units.insertAfter(assign, unitList.get(jimpleUtil.getAtStmtNumber(units) - 1));
        Unit specialInit = jimpleUtil.specialCallStatement(paramRef,
                HttpServletImpl.getMethod("void <init>()").toString());
        units.insertAfter(specialInit, assign);
        return paramRef;
    }

    @Override
    public Local mockHttpSession(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        Local paramRef = jimpleUtil.newLocalVar(sootClass.getShortName().toLowerCase(), sootClass.getName());
        Local existLocal = jimpleUtil.checkLocalExit(body, paramRef);
        if (existLocal != null) {
            return existLocal;
        }
        body.getLocals().add(paramRef);
        SootClass HttpSessionImpl = gsc.generateHttpSession(sootClass);
        List<Unit> unitList = new ArrayList<>(units);
        Unit assign = jimpleUtil.createAssignStmt(paramRef, HttpSessionImpl.getName());
        units.insertAfter(assign, unitList.get(jimpleUtil.getAtStmtNumber(units) - 1));
        Unit specialInit = jimpleUtil.specialCallStatement(paramRef,
                HttpSessionImpl.getMethod("void <init>()").toString());
        units.insertAfter(specialInit, assign);
        return paramRef;
    }

    @Override
    public Local mockFilterChain(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        Local paramRef = jimpleUtil.newLocalVar(sootClass.getShortName().toLowerCase(), sootClass.getName());
        Local existLocal = jimpleUtil.checkLocalExit(body, paramRef);
        if (existLocal != null) {
            return existLocal;
        }
        body.getLocals().add(paramRef);
        List<Unit> unitList = new ArrayList<>(units);
        Unit assign = jimpleUtil.createAssignStmt(paramRef, sootClass.toString());
        units.insertAfter(assign, unitList.get(jimpleUtil.getAtStmtNumber(units) - 1));
        SootClass FilterChainImpl = gsc.generateFilterChain(sootClass);
        Unit specialInit = jimpleUtil.specialCallStatement(paramRef,
                FilterChainImpl.getMethod("void <init>()").toString());
        units.insertAfter(specialInit, assign);
        return paramRef;
    }
}
