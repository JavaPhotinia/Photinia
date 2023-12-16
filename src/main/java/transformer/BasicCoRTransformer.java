package transformer;

import backend.GenerateSyntheticClass;
import backend.GenerateSyntheticClassImpl;
import bean.GotoEdge;
import bean.HandlerTargetModel;
import bean.InsertMethod;
import bean.ProxyModel;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JIdentityStmt;
import utils.JimpleUtil;

import java.util.*;

public abstract class BasicCoRTransformer {
    protected JimpleUtil jimpleUtil = new JimpleUtil();
    protected GenerateSyntheticClass gsc = new GenerateSyntheticClassImpl();
    protected boolean newVersion = false;
    public static Map<String, InsertMethod> insertMethodMap = new HashMap<>();
    public static Map<String, SootClass> proxyClassMap = new HashMap<>();
    public static Map<String, SootMethod> proxyMethodMap = new HashMap<>();
    public static Map<String, SootClass> proxyMap = new HashMap<>();

    public void modifyJimpleBody(SootMethod method) {
        JimpleBody body = (JimpleBody) method.retrieveActiveBody();
        List<Integer> returnList = new ArrayList<>();
        List<Integer> insertPointList = new ArrayList<>();
        PatchingChain<Unit> units = body.getUnits();
        if (method.getDeclaringClass().getShortName().contains("$$")) {
            units.removeIf(unit -> !(unit instanceof JIdentityStmt || unit.toString().contains("localTarget = this.")));
            addExtMockLocal(body);
            Type returnType = method.getReturnType();
            if (returnType instanceof VoidType) {
                jimpleUtil.addVoidReturnStmt(units);
            } else {
                Local returnRef = jimpleUtil.addLocalVar("returnRef", returnType, body);
                jimpleUtil.addCommonReturnStmt(returnRef, units);
            }
        }
        returnList.add(units.size() - 1);
        insertPointList.add(units.size() - 1);
        insertMethodMap.put(method.toString(), new InsertMethod(method, returnList, insertPointList));
    }

    abstract void addExtMockLocal(JimpleBody body);

    public void addAdviceToTarget(HandlerTargetModel targetModel) {
        String proxyClassName = this.getProxyClassName(targetModel.getSootClass());
        if (proxyClassName != null) {
            List<Type> additionalParam = this.additionalParamForMockProxy();
            SootClass proxyClass;
            if (proxyMap.containsKey(targetModel.getClassName() + "_" + this.getAnalysisName())) {
                proxyClass = proxyMap.get(targetModel.getClassName() + "_" + this.getAnalysisName());
            } else {
                proxyClass = gsc.generateProxy(targetModel.getSootClass(), proxyClassName, additionalParam);
                proxyMap.put(targetModel.getClassName() + "_" + this.getAnalysisName(), proxyClass);
            }
            SootMethod proxyMethod;
            if (additionalParam.size() > 0) {
                proxyMethod = proxyClass.getMethod(getProxyMethodSig(targetModel.getSootMethod()));
            } else {
                proxyMethod = proxyClass.getMethod(targetModel.getSootMethod().getSubSignature());
            }
            saveProxy(proxyClass, proxyMethod, targetModel);
            proxyClassMap.put(targetModel.getClassName(), proxyClass);
            proxyMethodMap.put(targetModel.getMethodName(), proxyMethod);
        } else if (this.getPreMethod() != null) {
            SootMethod preMethod = this.getPreMethod();
            saveProxy(null, preMethod, targetModel);
            proxyMethodMap.put(targetModel.getMethodName(), preMethod);
        }
    }

    private void saveProxy(SootClass proxyClass, SootMethod proxyMethod, HandlerTargetModel targetModel) {
        ProxyModel proxyModel = new ProxyModel();
        proxyModel.setProxyClass(proxyClass);
        if (proxyClass != null) {
            proxyModel.setProxyClassName(proxyClass.getName());
        }
        proxyModel.setProxyMethod(proxyMethod);
        proxyModel.setProxyMethodName(proxyMethod.getSignature());
        proxyModel.setSootMethod(targetModel.getSootMethod());
        if (!targetModel.getProxyModelMap().containsKey(this.getAnalysisName())) {
            targetModel.getProxyModelMap().put(this.getAnalysisName(), proxyModel);
        }
    }

    public String getProxyMethodSig(SootMethod targetMethod) {
        List<Type> oriParamList = targetMethod.getParameterTypes();
        List<Type> finalParamList = new ArrayList<>(oriParamList);
        Set<Type> paramSet = new HashSet<>(oriParamList);
        for (Type type : this.additionalParamForMockProxy()) {
            if (!paramSet.contains(type)) {
                finalParamList.add(type);
            }
        }
        SootMethod subMethod = new SootMethod(targetMethod.getName(), finalParamList, targetMethod.getReturnType(), targetMethod.getModifiers());
        return subMethod.getSubSignature();
    }


    abstract public String getAnalysisName();

    abstract String getProxyClassName(SootClass targetClass);

    public abstract List<Type> additionalParamForMockProxy();

    public boolean getVersion() {
        return this.newVersion;
    }

    abstract public GotoEdge gotoLink();

    abstract public SootMethod getPreMethod();

    abstract public boolean addFormalParamForAround();

    abstract public boolean addSelfClassFormalParamForAround();

    abstract public boolean specialPoint(Unit unit);

}
