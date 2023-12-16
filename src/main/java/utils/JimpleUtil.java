package utils;

import backend.MockObjectImpl;
import bean.ConstructorArgBean;
import bean.DBColumnBean;
import bean.DBMethodBean;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JimpleLocal;

import java.util.*;

import static analysis.CreateEdge.beansAndMethods;
import static backend.GenerateSyntheticClassImpl.alreadyInitBean;
import static utils.StringConstantUtil.OBJECT_CLASS;

public class JimpleUtil extends BaseJimpleUtil {
    private final SootClass collectionFactory = Scene.v().getSootClass("java.util.Collections");

    public Map<Type, Type> getImplType() {
        Map<Type, Type> map = new HashMap<>();
        map.put(RefType.v("java.util.List"), RefType.v("java.util.ArrayList"));
        map.put(RefType.v("java.util.Map"), RefType.v("java.util.HashMap"));
        map.put(RefType.v("java.util.Set"), RefType.v("java.util.HashSet"));
        return map;
    }

    public JimpleBody createMainJimpleBody(SootMethod method) {
        // Create a body for the main method and set it as the active body
        JimpleBody body = Jimple.v().newBody(method);
        // Create a local to hold the main method argument
        // Note: In general for any use of objects or basic-types, must generate a local to
        // hold that in the method body
        Local frm1 = Jimple.v().newLocal("frm1", ArrayType.v(RefType.v("java.lang.String"), 1));
        body.getLocals().add(frm1);
        // Create a unit (or statement) that assigns main's formal param into the local arg
        PatchingChain<Unit> units = body.getUnits();
        units.add(Jimple.v().newIdentityStmt(frm1, Jimple.v().newParameterRef(ArrayType.v(RefType.v("java.lang.String"), 1), 0)));
        units.add(Jimple.v().newReturnVoidStmt());
        return body;
    }

    public SootMethod genDefaultConstructor(SootClass customImplClass) {
        return genDefaultConstructor(customImplClass, null, false);
    }

    public SootMethod genDefaultConstructor(SootClass customImplClass, SootField field, boolean singleton) {
        SootMethod initMethod = new SootMethod("<init>", null, VoidType.v(), Modifier.PUBLIC);
        SootMethod signature = getInitMethod(customImplClass.getSuperclass());
        JimpleBody initBody = createInitJimpleBody(initMethod, signature, customImplClass.getName(), field, singleton);
        initMethod.setActiveBody(initBody);
        return initMethod;
    }

    /**
     * Construct the default init method
     *
     * @param fields  fields of class
     * @param collect
     * @return
     */
    public SootMethod genDefaultClinit(Set<SootField> fields, Map<String, List<ConstructorArgBean>> collect) {
        SootMethod initMethod = new SootMethod("<clinit>", null, VoidType.v(), Modifier.STATIC);
        initMethod.setActiveBody(createClinitJimpleBody(initMethod, fields, collect));
        return initMethod;
    }

    public SootMethod genStaticCustomMethod(String methodName, List<Type> parameterTypes, Type returnType, SootField field) {
        SootMethod implMethod = new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC + Modifier.STATIC);
        implMethod.setActiveBody(createSetAndGetStaticBody(implMethod, parameterTypes, returnType, field));
        return implMethod;
    }

    public SootMethod genCustomMethod(SootClass customImplClass, String methodName, List<Type> parameterTypes, Type returnType) {
        SootMethod implMethod = new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
        implMethod.setActiveBody(createNewJimpleBody(implMethod, new ArrayList<>(), customImplClass.getName()));
        return implMethod;
    }

    public SootMethod genCustomMethodWithCall(SootClass customImplClass, String methodName, List<Type> parameterTypes, Type returnType, List<SootMethod> signatures) {
        SootMethod implMethod = new SootMethod(methodName, parameterTypes, returnType, Modifier.PRIVATE);
        implMethod.setActiveBody(createNewJimpleBody(implMethod, signatures, customImplClass.getName()));
        return implMethod;
    }

    public SootMethod genCustomMethodWithField(String methodName, SootClass customImplClass, List<Type> parameterTypes, Type returnType, SootField field) {
        SootMethod implMethod = new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
        implMethod.setActiveBody(createNewJimpleBody(implMethod, customImplClass.getName(), field, parameterTypes, returnType));
        return implMethod;
    }

    public SootMethod genSQLMethod(SootClass customImplClass, String methodName, List<Type> parameterTypes, Type returnType, DBMethodBean dbMethodBean) {
        SootMethod implMethod = new SootMethod(methodName, parameterTypes, returnType, Modifier.PUBLIC);
        implMethod.setActiveBody(createNewSQLMethodJimpleBody(implMethod, customImplClass.getName(), dbMethodBean));
        return implMethod;
    }

    public JimpleBody createInitJimpleBody(SootMethod method, SootMethod signature, String cName, SootField field, boolean singleton) {
        SootClass singletonFactory = Scene.v().getSootClass("synthetic.method.SingletonFactory");
        JimpleBody body = newMethodBody(method);
        Local thisRef = addLocalVar("this", cName, body);
        PatchingChain<Unit> units = body.getUnits();
        createIdentityStmt(thisRef, createThisRef(cName), units);
        units.add(specialCallStatement(thisRef, signature.toString(), Collections.emptyList()));

        if (field != null) {
            String vtype = field.getType().toString();
            Local tmpRef = addLocalVar(vtype.substring(vtype.lastIndexOf(".") + 1).toLowerCase(), vtype, body);
            if (!singleton) {
                createAssignStmt(tmpRef, createNewExpr(field.getType().toString()), units);
                units.add(specialCallStatement(tmpRef, Scene.v().getSootClass(field.getType().toString()).getMethod("void <init>()").toString()));
            } else {
                Value returnValue = createStaticInvokeExpr(singletonFactory.getMethod(((RefType) field.getType()).getSootClass().getName() + " get" + ((RefType) field.getType()).getSootClass().getShortName() + "()"));
                createAssignStmt(tmpRef, returnValue, units);
            }
            createAssignStmt(createInstanceFieldRef(thisRef, field.makeRef()), tmpRef, units);
        }
        addVoidReturnStmt(units);
        return body;
    }

    public JimpleBody createClinitJimpleBody(SootMethod method, Set<SootField> fields, Map<String, List<ConstructorArgBean>> collect) {
        boolean hasargFlag = collect != null && collect.size() > 0;
        JimpleBody body = newMethodBody(method);
        PatchingChain<Unit> units = body.getUnits();
        for (SootField field : fields) {
            String vtype = field.getType().toString();
            Local tmpRef = addLocalVar(vtype.substring(vtype.lastIndexOf(".") + 1).toLowerCase(), vtype, body);
            createAssignStmt(tmpRef, createNewExpr(field.getType().toString()), units);
            if (hasargFlag && collect.containsKey(vtype)) {
                SootClass argClazz = Scene.v().getSootClass(vtype);
                List<ConstructorArgBean> constructorArgBeans = collect.get(vtype);
                for (SootMethod argClazzMethod : argClazz.getMethods()) {
                    if (argClazzMethod.getName().contains("<init>")
                            && argClazzMethod.getParameterCount() == constructorArgBeans.size()) {
                        List<Unit> argunitlist = new LinkedList<>(argClazzMethod.retrieveActiveBody().getUnits());
                        LinkedHashMap<String, String> parammap = new LinkedHashMap<>();
                        for (int i = 1; i <= argClazzMethod.getParameterCount(); i++) {
                            if (argunitlist.get(i) instanceof JIdentityStmt) {
                                JIdentityStmt stmt = (JIdentityStmt) argunitlist.get(i);
                                String key = ((JimpleLocal) stmt.leftBox.getValue()).getName();
                                String val = stmt.rightBox.getValue().getType().toString();
                                parammap.put(key, val);
                            }
                        }
                        int index = 0;
                        boolean flag = true;
                        List<Value> params = new ArrayList<>();
                        while (index < constructorArgBeans.size()) {
                            ConstructorArgBean argBean = constructorArgBeans.get(index);
                            if (argBean.getArgType() != null) {
                                Type parameterType = argClazzMethod.getParameterType(index);
                                if (!parameterType.toString().equals(argBean.getArgType())) {
                                    flag = false;
                                    break;
                                } else {
                                    params.add(getConstant(argBean.getArgType(), argBean.getArgValue(), false));
                                }
                            } else if (argBean.getArgName() != null) {
                                if (!parammap.containsKey(argBean.getArgName())) {
                                    flag = false;
                                    break;
                                } else {
                                    params.add(getConstant(parammap.get(argBean.getArgName()), argBean.getArgValue(), false));
                                }
                            } else if (argBean.getRefType() != null) {
                                if (!parammap.containsKey(argBean.getRefType())) {
                                    flag = false;
                                    break;
                                } else {
                                    params.add(getConstant("", "", true));
                                }
                            }
                            index++;
                        }
                        if (flag) {
                            units.add(specialCallStatement(tmpRef, argClazzMethod.toString(), params));
                            break;
                        }
                    }
                }
            } else {
                SootClass targetClass = Scene.v().getSootClass(field.getType().toString());
                if (targetClass.declaresMethodByName("<init>")) {
                    units.add(specialCallStatement(tmpRef, getInitMethod(targetClass).toString()));
                }
            }
            createAssignStmt(createStaticFieldRef(field.makeRef()), tmpRef, units);
        }
        addVoidReturnStmt(units);
        return body;
    }

    private Constant getConstant(String typesign, String value, boolean isclazz) {
        if (isclazz) {
            return NullConstant.v();
        }
        String s = typesign.toLowerCase();
        if (s.contains("string")) {
            return StringConstant.v(value);
        } else if (s.contains("int")) {
            return IntConstant.v(Integer.parseInt(value));
        } else if (s.contains("double")) {
            return DoubleConstant.v(Double.parseDouble(value));
        } else if (s.contains("float")) {
            return FloatConstant.v(Float.parseFloat(value));
        } else if (s.contains("long")) {
            return LongConstant.v(Long.parseLong(value));
        }
        return null;
    }

    public JimpleBody createNewJimpleBody(SootMethod method, List<SootMethod> signatures, String cName) {
        JimpleBody body = newMethodBody(method);
        Local thisRef = addLocalVar("this", cName, body);
        Local returnRef = null;
        Type returnType = method.getReturnType();
        PatchingChain<Unit> units = body.getUnits();
        createIdentityStmt(thisRef, createThisRef(cName), units);
        List<Value> parameterValues = new ArrayList<>(identityParam(method, body));
        if (method.getName().equals("setArgs_synthetic")) {
            SootField sootField = Scene.v().getSootClass(cName).getFieldByName("args");
            createAssignStmt(createInstanceFieldRef(thisRef, sootField.makeRef()), body.getParameterLocal(0), units);
        }

        if (method.getName().equals("setAttribute") && cName.equals("synthetic.method.HttpSessionImpl")) {
            SootField sootField = Scene.v().getSootClass(cName).getFieldByName("map");
            Local tmpRef = addLocalVar("tmpRef", sootField.getType(), body);
            createAssignStmt(tmpRef, createInstanceFieldRef(thisRef, sootField.makeRef()), units);
            List<Local> params = new ArrayList<>();
            params.add(body.getParameterLocal(0));
            params.add(body.getParameterLocal(1));
            SootMethod targetMethod = Scene.v().getSootClass(sootField.getType().toString()).getMethod("java.lang.Object put(java.lang.Object,java.lang.Object)");
            units.add(virtualCallStatement(tmpRef, targetMethod, params));
        }

        if (method.getName().equals("getAttribute") && cName.equals("synthetic.method.HttpSessionImpl")) {
            SootField sootField = Scene.v().getSootClass(cName).getFieldByName("map");
            Local tmpRef = addLocalVar("tmpRef", sootField.getType(), body);
            returnRef = addLocalVar("returnRef", method.getReturnType(), body);
            createAssignStmt(tmpRef, createInstanceFieldRef(thisRef, sootField.makeRef()), units);
            List<Local> params = new ArrayList<>();
            params.add(body.getParameterLocal(0));
            SootMethod targetMethod = Scene.v().getSootClass(sootField.getType().toString()).getMethod("java.lang.Object get(java.lang.Object)");
            createAssignStmt(returnRef, createVirtualInvokeExpr(returnRef, targetMethod, params), units);
        }
        HashMap<String, Local> declareName = new HashMap<>();
        for (int i = 0; i < signatures.size(); i++) {
            SootMethod toCall = Scene.v().getMethod(signatures.get(i).toString());
            List<Value> paramList = new ArrayList<>();
            for (int j = 0; j < toCall.getParameterCount() - parameterValues.size(); j++) {
                paramList.add(NullConstant.v());
            }
            paramList.addAll(parameterValues);

            String declaringClassName = signatures.get(i).getDeclaringClass().getName();
            if (!declaringClassName.equals(cName)
                    && !declaringClassName.equals(OBJECT_CLASS)
                    && signatures.get(i).getReturnType() != null) {
                if (!declareName.containsKey(declaringClassName)) {
                    Local virtualRef = addLocalVar("virtual" + i, declaringClassName, body);
                    createAssignStmt(virtualRef, declaringClassName, units);
                    SootMethod initMethod = getInitMethod(signatures.get(i).getDeclaringClass());
                    List<Value> params = new ArrayList<>();
                    for (Type ignored : initMethod.getParameterTypes()) {
                        params.add(NullConstant.v());
                    }
                    units.add(specialCallStatement(virtualRef, initMethod, params));
                    declareName.put(declaringClassName, virtualRef);
                    units.add(virtualCallStatement(virtualRef, toCall.toString(), paramList));
                    if (declaringClassName.equals(returnType.toString()) && returnRef == null) {
                        returnRef = virtualRef;
                    }
                } else {
                    Local virtualRef = declareName.get(declaringClassName);
                    units.add(virtualCallStatement(virtualRef, toCall.toString(), paramList));
                }
            } else {
                if (signatures.get(i).getReturnType() instanceof VoidType || method.getName().equals("callEntry_synthetic")) {
                    units.add(specialCallStatement(thisRef, toCall.toString(), paramList));
                }
            }
        }

        if (returnType instanceof RefType) {
            if (returnRef != null) {
                addCommonReturnStmt(returnRef, units);
            } else {
                addCommonReturnStmt(NullConstant.v(), units);
            }
        } else if (returnType instanceof VoidType) {
            addVoidReturnStmt(units);
        } else if (returnType instanceof IntType) {
            addCommonReturnStmt(IntConstant.v(0), units);
        } else if (returnType instanceof LongType) {
            addCommonReturnStmt(LongConstant.v(0), units);
        } else if (returnType instanceof DoubleType) {
            addCommonReturnStmt(DoubleConstant.v(0), units);
        } else if (returnType instanceof ArrayType) {
            if (Scene.v().getSootClass(cName).getFields().size() > 0 && !cName.equals("synthetic.method.HttpSessionImpl")) {
                SootField sootField = Scene.v().getSootClass(cName).getFieldByName("args");
                if (sootField != null && sootField.getType().equals(returnType) && method.getName().contains("getArgs")) {
                    returnRef = addLocalVar("returnRef", returnType, body);
                    createAssignStmt(returnRef, createInstanceFieldRef(thisRef, sootField.makeRef()), units);
                    addCommonReturnStmt(returnRef, units);
                }
            } else {
                addCommonReturnStmt(NullConstant.v(), units);
            }
        } else if (returnType instanceof BooleanType) {
            addCommonReturnStmt(IntConstant.v(0), units);
        }
        return body;
    }

    public JimpleBody createNewSQLMethodJimpleBody(SootMethod method, String cName, DBMethodBean dbMethodBean) {
        JimpleBody body = newMethodBody(method);
        Local thisRef = addLocalVar("this", cName, body);
        PatchingChain<Unit> units = body.getUnits();
        createIdentityStmt(thisRef, createThisRef(cName), units);
        Local returnRef = null;
        identityParam(method, body);
        Type returnType = method.getReturnType();
        SootClass tableClass = null;
        Local tableRef = null;
        Unit ifStmtInsert = null;
        if (dbMethodBean.getTableClassName() != null && !dbMethodBean.getTableClassName().equals("")) {
            tableClass = Scene.v().getSootClass(dbMethodBean.getTableClassName());
            tableRef = addLocalVar(tableClass.getShortName().toLowerCase() + "Ref", tableClass.getType(), body);
            Unit getInstanceUnit = createAssignStmt(tableRef, createStaticInvokeExpr(tableClass.getMethodByName("getInstance")));
            units.add(getInstanceUnit);
            ifStmtInsert = getInstanceUnit;
            if (dbMethodBean.getMethodType().equals("select")) {
                returnRef = processSelectMethod(returnType, dbMethodBean, body, units, tableClass, tableRef);
            } else if (dbMethodBean.getMethodType().equals("insert") || dbMethodBean.getMethodType().equals("update")) {
                processUpdateOrInsertMethod(dbMethodBean, method, body, units, tableClass, tableRef);
            } else {
                // delete
            }
        }

        // for (DBColumnBean sqlParamName : dbMethodBean.getSqlParamNames()) {
        //     String methodName = "set" + sqlParamName.getValueName().toLowerCase() + "Inject";
        //     if (tableClass != null && tableClass.declaresMethodByName(methodName)) {
        //         SootMethod tableMethod = tableClass.getMethodByName(methodName);
        //         System.err.println(tableMethod.getSignature() + " -> _SINK_");
        //         try {
        //             Local paramRef = body.getParameterLocal(sqlParamName.getParamIndex());
        //             units.add(virtualCallStatement(tableRef, tableMethod, Collections.singletonList(paramRef)));
        //         } catch (Exception e) {
        //             continue;
        //         }
        //     } else {
        //         System.out.println(methodName);
        //     }
        // }
        addReturn(returnType, units, returnRef, body);

        if (tableClass != null && tableRef != null && ifStmtInsert != null && dbMethodBean.getWhereConditionList().size() > 0) {
            addNullReturn(returnType, units);
            String link = "";
            for (DBColumnBean dbColumnBean : dbMethodBean.getWhereConditionList()) {
                String methodName = "get" + dbColumnBean.getParamName().replace("_", "").toLowerCase();
                if (!tableClass.declaresMethodByName(methodName)) {
                    continue;
                }
                SootMethod tableMethod = tableClass.getMethodByName(methodName);
                Local tmpRef = addLocalVar("tmp" + dbColumnBean.getParamName(), tableMethod.getReturnType(), body);
                Unit conditionUnit = createAssignStmt(tmpRef, createVirtualInvokeExpr(tableRef, tableMethod));
                units.insertAfter(conditionUnit, ifStmtInsert);
                ifStmtInsert = conditionUnit;
                if (link.equals("") || link.equals("and")) {
                    Unit ifUint;
                    if (dbColumnBean.getValueType() != null && dbColumnBean.getValueType().equals("int")) {
                        ifUint = createIfWithNe(tmpRef, IntConstant.v(Integer.parseInt(dbColumnBean.getValueName())), units.getLast());
                    } else if (dbColumnBean.getParamIndex() < 0) {
                        ifUint = createIfWithNe(tmpRef, StringConstant.v(dbColumnBean.getValueName()), units.getLast());
                    } else if (body.getParameterLocals().size() > 0) {
                        ifUint = createIfWithNe(tmpRef, body.getParameterLocal(dbColumnBean.getParamIndex()), units.getLast());
                    } else {
                        continue;
                    }
                    units.insertAfter(ifUint, ifStmtInsert);
                    ifStmtInsert = ifUint;
                } else {
                    // other
                }
                link = dbColumnBean.getLink();
            }
        }
        return body;
    }

    private Local processSelectMethod(Type returnType, DBMethodBean dbMethodBean, JimpleBody body, PatchingChain<Unit> units, SootClass tableClass, Local tableRef) {
        Local returnRef = null;
        if (returnType instanceof RefType && !filterBaseClass(returnType)) {
            SootClass refClass = ((RefType) returnType).getSootClass();
            boolean isCollectType = false;
            Type tmpType = returnType;
            if (filterCollectClass(returnType)) {
                refClass = dbMethodBean.getGenerics();
                tmpType = refClass.getType();
                isCollectType = true;
            }
            if (checkLocalExist(body, refClass.getShortName().toLowerCase()) == null && refClass.declaresMethodByName("<init>")) {
                SootMethod initMethod = getInitMethod(refClass);
                returnRef = addLocalVar(refClass.getShortName().toLowerCase(), tmpType, body);
                units.add(createAssignStmt(returnRef, refClass.getName()));
                units.add(specialCallStatement(returnRef, initMethod, fillParameters(null, body, initMethod)));
            }
            for (DBColumnBean dbColumnBean : dbMethodBean.getDbColumnBeanList()) {
                String methodName = "get" + dbColumnBean.getParamName().replace("_", "").toLowerCase();
                if (!tableClass.declaresMethodByName(methodName)) {
                    continue;
                }
                SootMethod tableMethod = tableClass.getMethodByName(methodName);
                Local tmpRef = addLocalVar("table_" + dbColumnBean.getValueName(), tableMethod.getReturnType(), body);
                units.add(createAssignStmt(tmpRef, createVirtualInvokeExpr(tableRef, tableMethod)));
                if (!filterBaseClass(tmpType)) {
                    SootMethod ormMethod = SootUtil.findSpecialMethodIgnoreCase("set" + dbColumnBean.getValueName(), refClass);
                    if (ormMethod == null) {
                        ormMethod = SootUtil.findSpecialMethodIgnoreCase(dbColumnBean.getValueName(), refClass);
                    }
                    if (ormMethod != null) {
                        units.add(virtualCallStatement(returnRef, ormMethod, Collections.singletonList(tmpRef)));
                    }
                } else {
                    returnRef = tmpRef;
                }
            }
            if (isCollectType && returnRef != null) {
                if (returnType.toString().equals("org.springframework.data.domain.Page")) {
                    Local pageTmp = addLocalVar("pageTmp", returnType, body);
                    units.add(caseAssignStmt(pageTmp, returnRef, returnType));
                    returnRef = pageTmp;
                } else {
                    Local collectRef = addLocalVar("collectRef", returnType, body);
                    units.add(staticCallAndAssignStatement(collectRef, collectionFactory.getMethodByName("singletonList"), returnRef));
                    returnRef = collectRef;
                }
            }
        } else if (dbMethodBean.getDbColumnBeanList().size() > 0) {
            DBColumnBean dbColumnBean = dbMethodBean.getDbColumnBeanList().get(0);
            SootMethod tableMethod = tableClass.getMethodByName("get" + dbColumnBean.getParamName()
                    .replace("_", "").toLowerCase());
            Local tmpRef = addLocalVar("table_" + dbColumnBean.getParamName().replace("_", ""), tableMethod.getReturnType(), body);
            units.add(createAssignStmt(tmpRef, createVirtualInvokeExpr(tableRef, tableMethod)));
            returnRef = tmpRef;
        } else {
            System.err.println(dbMethodBean);
        }
        return returnRef;
    }

    private void processUpdateOrInsertMethod(DBMethodBean dbMethodBean, SootMethod method, JimpleBody body, PatchingChain<Unit> units, SootClass tableClass, Local tableRef) {
        for (DBColumnBean dbColumnBean : dbMethodBean.getDbColumnBeanList()) {
            if (dbColumnBean.isProcess()) {
                continue;
            }
            if (dbColumnBean.getParamIndex() < 0) {
                String methodName = "set" + dbColumnBean.getParamName().replace("_", "").toLowerCase();
                if (tableClass.declaresMethodByName(methodName)) {
                    SootMethod tableMethod = tableClass.getMethodByName(methodName);
                    String valueName = dbColumnBean.getValueName();
                    if ((dbColumnBean.getValueType() != null && dbColumnBean.getValueType().equals("int"))
                            || tableMethod.getParameterTypes().get(0) instanceof IntType
                            || tableMethod.getParameterTypes().get(0).toString().equals("java.lang.Integer")) {
                        Value tmpRef = IntConstant.v(1);
                        units.add(virtualCallStatement(tableRef, tableMethod, Collections.singletonList(tmpRef)));
                        dbColumnBean.setProcess(true);
                    } else if (tableMethod.getParameterTypes().get(0).toString().equals("java.lang.Integer")) {
                        if (valueName == null) {
                            valueName = "";
                        }
                        Value tmpRef = StringConstant.v(valueName);
                        units.add(virtualCallStatement(tableRef, tableMethod, Collections.singletonList(tmpRef)));
                        dbColumnBean.setProcess(true);
                    }
                } else {
                    System.err.println("no find this method " + methodName);
                }
            } else {
                Type paramType = method.getParameterType(dbColumnBean.getParamIndex());
                if (paramType instanceof RefType && !filterBaseClass(paramType)) {
                    SootClass refClass = ((RefType) paramType).getSootClass();
                    Local paramRef = body.getParameterLocal(dbColumnBean.getParamIndex());
                    Type tmpType = paramType;
                    if (filterCollectClass(paramType)) {
                        SootClass tmpClass = dbMethodBean.getGenerics();
                        tmpType = tmpClass.getType();
                        Local tmpRef = addLocalVar(tmpClass.getShortName().toLowerCase() + "Ref", tmpType, body);
                        SootMethod getMethod = refClass.getMethodByName("get");
                        Local obj = addLocalVar("obj", getMethod.getReturnType(), body);
                        units.add(createAssignStmt(obj, createInterfaceInvokeExpr(paramRef, refClass.getMethodByName("get"), Collections.singletonList(IntConstant.v(0)))));
                        units.add(caseAssignStmt(tmpRef, obj, tmpType));
                        refClass = tmpClass;
                        paramRef = tmpRef;
                    }
                    String valueName = dbColumnBean.getValueName().replaceAll("[(#{}]", "");
                    String methodName = "set" + dbColumnBean.getParamName().replace("_", "").toLowerCase();
                    if (tableClass.declaresMethodByName(methodName)) {
                        SootMethod tableMethod = tableClass.getMethodByName(methodName);
                        Value tmpRef;
                        dbColumnBean.setProcess(true);
                        if (!filterBaseClass(tmpType)) {
                            boolean baseClassFlag = false;
                            SootMethod ormMethod = SootUtil.findSpecialMethodIgnoreCase("get" + valueName, refClass);
                            if (ormMethod != null) {
                                tmpRef = addLocalVar(valueName.toLowerCase(), ormMethod.getReturnType(), body);
                                units.add(createAssignStmt(tmpRef, createVirtualInvokeExpr(paramRef, ormMethod)));
                            } else {
                                tmpRef = StringConstant.v(valueName);
                            }
                            tmpRef = addCaseAssignStmt(tmpRef, tableMethod, body, units, baseClassFlag);
                            units.add(virtualCallStatement(tableRef, tableMethod, Collections.singletonList(tmpRef)));
                        } else {
                            tmpRef = paramRef;
                            tmpRef = addCaseAssignStmt(tmpRef, tableMethod, body, units, false);
                            units.add(virtualCallStatement(tableRef, tableMethod, Collections.singletonList(tmpRef)));
                            break;
                        }
                    } else {
                        System.err.println("no find this method " + methodName);
                    }
                } else {
                    String methodName = "set" + dbColumnBean.getParamName().replace("_", "").toLowerCase();
                    if (tableClass.declaresMethodByName(methodName)) {
                        SootMethod tableMethod = tableClass.getMethodByName(methodName);
                        Value param = body.getParameterLocal(dbColumnBean.getParamIndex());
                        param = addCaseAssignStmt(param, tableMethod, body, units, false);
                        units.add(virtualCallStatement(tableRef, tableMethod, Collections.singletonList(param)));
                        dbColumnBean.setProcess(true);
                    } else {
                        System.err.println("no find this method " + methodName);
                    }
                }
            }
        }
    }

    private void addNullReturn(Type returnType, PatchingChain<Unit> units) {
        if (returnType instanceof RefType) {
            addCommonReturnStmt(NullConstant.v(), units);
        } else if (returnType instanceof VoidType) {
            addVoidReturnStmt(units);
        } else if (returnType instanceof IntType) {
            addCommonReturnStmt(IntConstant.v(0), units);
        } else if (returnType instanceof LongType) {
            addCommonReturnStmt(LongConstant.v(0), units);
        } else if (returnType instanceof DoubleType) {
            addCommonReturnStmt(DoubleConstant.v(0), units);
        } else if (returnType instanceof ArrayType) {
            addCommonReturnStmt(NullConstant.v(), units);
        } else if (returnType instanceof BooleanType) {
            addCommonReturnStmt(IntConstant.v(0), units);
        }
    }

    private void addReturn(Type returnType, PatchingChain<Unit> units, Local returnRef, JimpleBody body) {
        if (returnType instanceof RefType) {
            Value returnValue = NullConstant.v();
            if (returnRef != null) returnValue = returnRef;
            addCommonReturnStmt(returnValue, units);
        } else if (returnType instanceof VoidType) {
            addVoidReturnStmt(units);
        } else if (returnType instanceof IntType) {
            Value returnValue = IntConstant.v(1);
            if (returnRef != null) returnValue = caseReturn(body, units, returnRef, IntType.v());
            addCommonReturnStmt(returnValue, units);
        } else if (returnType instanceof LongType) {
            Value returnValue = LongConstant.v(1);
            if (returnRef != null) returnValue = caseReturn(body, units, returnRef, LongType.v());
            addCommonReturnStmt(returnValue, units);
        } else if (returnType instanceof DoubleType) {
            Value returnValue = DoubleConstant.v(1);
            if (returnRef != null) returnValue = caseReturn(body, units, returnRef, DoubleType.v());
            addCommonReturnStmt(returnValue, units);
        } else if (returnType instanceof ArrayType) {
            addCommonReturnStmt(NullConstant.v(), units);
        } else if (returnType instanceof BooleanType) {
            Value returnValue = IntConstant.v(1);
            if (returnRef != null) returnValue = caseReturn(body, units, returnRef, BooleanType.v());
            addCommonReturnStmt(returnValue, units);
        }
    }

    private Value caseReturn(JimpleBody body, PatchingChain<Unit> units, Local returnRef, Type type) {
        if (returnRef.getType() instanceof PrimType) {
            return returnRef;
        } else {
            Value returnValue = addLocalVar("tmpReturn", type, body);
            units.add(caseAssignStmt(returnValue, returnRef, type));
            return returnValue;
        }
    }

    private Value addCaseAssignStmt(Value tmpRef, SootMethod tableMethod, JimpleBody body, PatchingChain<Unit> units, boolean baseClassFlag) {
        if (!tmpRef.getType().equals(tableMethod.getParameterType(0)) && !baseClassFlag) {
            // if (tmpRef.getType() instanceof PrimType || tableMethod.getParameterType(0) instanceof PrimType) {
            //     return NullConstant.v();
            // }
            Local caseRef = addLocalVar("caseRef", tableMethod.getParameterType(0), body);
            units.add(caseAssignStmt(caseRef, tmpRef, tableMethod.getParameterType(0)));
            return caseRef;
        }
        return tmpRef;
    }

    public JimpleBody createSetAndGetStaticBody(SootMethod method, List<Type> parameterTypes, Type returnType, SootField field) {
        JimpleBody body = newMethodBody(method);
        PatchingChain<Unit> units = body.getUnits();
        if (parameterTypes != null && parameterTypes.size() > 0) {
            Local param = addLocalVar("param0", parameterTypes.get(0), body);
            createIdentityStmt(param, createParamRef(parameterTypes.get(0), 0), units);
            createAssignStmt(createStaticFieldRef(field.makeRef()), param, units);
            addVoidReturnStmt(units);
        } else {
            Local localRef = addLocalVar(field.getName().toLowerCase(), field.getType(), body);
            createAssignStmt(localRef, createStaticFieldRef(field.makeRef()), units);
            if (returnType instanceof RefType) {
                addCommonReturnStmt(localRef, units);
            }
        }
        return body;
    }

    public JimpleBody createNewJimpleBody(SootMethod method, String cName, SootField field, List<Type> parameterTypes, Type returnType) {
        JimpleBody body = newMethodBody(method);
        PatchingChain<Unit> units = body.getUnits();
        Local thisRef = addLocalVar("this", cName, body);
        createIdentityStmt(thisRef, createThisRef(cName), units);
        if (returnType instanceof RefType) {
            Local localRef = addLocalVar(field.getName().toLowerCase(), field.getType(), body);
            createAssignStmt(localRef, createInstanceFieldRef(thisRef, field.makeRef()), units);
            addCommonReturnStmt(localRef, units);
        } else if (returnType instanceof VoidType) {
            if (parameterTypes.size() == 1) {
                Local paramLocal = addLocalVar(field.getName().toLowerCase(), field.getType(), body);
                createIdentityStmt(paramLocal, createParamRef(parameterTypes.get(0), 0), units);
                createAssignStmt(createInstanceFieldRef(thisRef, field.makeRef()), paramLocal, units);
            }
            addVoidReturnStmt(units);
        } else if (returnType instanceof IntType) {
            addCommonReturnStmt(IntConstant.v(1), units);
        } else if (returnType instanceof LongType) {
            addCommonReturnStmt(LongConstant.v(1), units);
        } else if (returnType instanceof DoubleType) {
            addCommonReturnStmt(DoubleConstant.v(1), units);
        } else if (returnType instanceof ArrayType) {
            addCommonReturnStmt(NullConstant.v(), units);
        } else if (returnType instanceof BooleanType) {
            addCommonReturnStmt(IntConstant.v(1), units);
        }
        return body;
    }

    private List<Value> identityParam(SootMethod method, JimpleBody body) {
        PatchingChain<Unit> units = body.getUnits();
        List<Value> parameterValues = new ArrayList<>();
        for (int i = 0; i < method.getParameterCount(); i++) {
            Type parameterType = method.getParameterType(i);
            Local param = addLocalVar("param" + i, parameterType, body);
            createIdentityStmt(param, createParamRef(parameterType, i), units);
            parameterValues.add(param);
        }
        return parameterValues;
    }

    public JimpleBody createJimpleBody(SootMethod method, List<SootMethod> signatures, String cName) {
        Value virtualCallWithReturn = null;
        JimpleBody body = newMethodBody(method);
        Local thisRef = addLocalVar("this", cName, body);
        PatchingChain<Unit> units = body.getUnits();
        createIdentityStmt(thisRef, createThisRef(cName), units);
        // identity param
        identityParam(method, body);
        for (int i = 0; i < signatures.size(); i++) {
            SootMethod toCall = Scene.v().getMethod(signatures.get(i).toString());
            List<Value> paramList = fillParameters(method, body, toCall);
            String declaringClassName = signatures.get(i).getDeclaringClass().getName();

            if (toCall.isStatic() && paramList.size() == 1) {
                units.add(staticCallStatement(toCall, paramList.get(0)));
            } else if (!declaringClassName.equals(cName) && !declaringClassName.equals(OBJECT_CLASS) && signatures.get(i).getReturnType() != null) {
                Local virtualRef = addLocalVar("virtual" + i, declaringClassName, body);
                createAssignStmt(virtualRef, declaringClassName, units);
                SootMethod initMethod = getInitMethod(signatures.get(i).getDeclaringClass());
                units.add(specialCallStatement(virtualRef, initMethod.getSignature(), fillParameters(method, body, initMethod)));
                if (!(method.getReturnType() instanceof VoidType)) {
                    virtualCallWithReturn = createVirtualInvokeExpr(virtualRef, toCall, paramList);
                } else {
                    units.add(specialCallStatement(virtualRef, toCall, paramList));
                }
            } else {
                if (!(method.getReturnType() instanceof VoidType)
                        && !(signatures.get(i).getReturnType() instanceof VoidType)
                        && !(method.getName().startsWith("callEntry_synthetic"))) {
                    virtualCallWithReturn = createSpecialInvokeExpr(thisRef, toCall, paramList);
                } else {
                    units.add(specialCallStatement(thisRef, toCall, paramList));
                }
            }
        }

        Type returnType = method.getReturnType();
        if (returnType instanceof RefType) {
            Local returnRef = addLocalVar("returnRef", returnType, body);
            if (((RefType) returnType).getSootClass().isInterface()) {
                returnType = getImplType().get(returnType);
            }
            if (virtualCallWithReturn != null) {
                createAssignStmt(returnRef, virtualCallWithReturn, units);
            } else {
                createAssignStmt(returnRef, createNewExpr((RefType) returnType), units);
                units.add(specialCallStatement(returnRef,
                        ((RefType) returnType).getSootClass().getMethod("void <init>()")));
            }
            addCommonReturnStmt(returnRef, units);
        } else if (returnType instanceof VoidType) {
            addVoidReturnStmt(units);
        } else if (returnType instanceof IntType) {
            addCommonReturnStmt(LongConstant.v(1), units);
        } else if (returnType instanceof LongType) {
            addCommonReturnStmt(LongConstant.v(1), units);
        } else if (returnType instanceof DoubleType) {
            addCommonReturnStmt(DoubleConstant.v(1), units);
        }
        return body;
    }

    public List<Value> fillParameters(SootMethod method, JimpleBody body, SootMethod toCall) {
        SootClass singletonFactory = Scene.v().getSootClass("synthetic.method.SingletonFactory");
        List<Value> paramList = new ArrayList<>();
        for (int j = 0; j < toCall.getParameterCount(); j++) {
            if (isBaseTypes(toCall.getParameterType(j))) {
                paramList.add(NullConstant.v());
                continue;
            }
            SootClass sootClass = Scene.v().getSootClass(toCall.getParameterType(j).toString());
            if (!sootClass.isInterface() && !sootClass.isJavaLibraryClass()
                    && !sootClass.isAbstract()
                    && (sootClass.getMethods().size() > 0 || beansAndMethods.containsKey(sootClass))) {
                paramList.add(mockParamOfBean(body, body.getUnits(), sootClass, toCall));
            } else if (sootClass.getName().contains("ServletRequest")) {
                paramList.add(getLocalForServlet(body, "ServletRequest", sootClass));
            } else if (sootClass.getName().contains("ServletResponse")) {
                paramList.add(getLocalForServlet(body, "ServletResponse", sootClass));
            } else if (sootClass.getName().equals("java.lang.String")) {
                if (method != null) {
                    paramList.add(mockParamOfString(method, body, body.getUnits(), sootClass));
                } else {
                    paramList.add(StringConstant.v(""));
                }
            } else if (sootClass.getName().contains("HttpSession")) {
                paramList.add(getLocalForSession(body, "HttpSession", sootClass));
            } else if (sootClass.getName().contains("FilterChain")) {
                paramList.add(mockParamOfFilterChain(body, body.getUnits(), sootClass));
            } else if (sootClass.getName().contains("JoinPoint")) {
                paramList.add(getLocalForJoinPoint(body, sootClass));
            } else if (singletonFactory.declaresMethodByName("get" + sootClass.getShortName())) {
                paramList.add(getLocalFromSingletonFactory(body, body.getUnits(), sootClass));
            } else {
                paramList.add(NullConstant.v());
            }
        }
        return paramList;
    }

    public Local initLocalModel(SootMethod currentMethod, SootMethod calleeMethod, JimpleBody body, PatchingChain<Unit> units, Local localModel) {
        Local existLocal = checkLocalExist(body, localModel.getName());
        if (calleeMethod.getDeclaringClass().getName().equals(currentMethod.getDeclaringClass().getName())) {
            localModel = body.getThisLocal();
        } else if (existLocal == null) {
            body.getLocals().add(localModel);
            Unit localInitAssign = createAssignStmt(localModel, calleeMethod.getDeclaringClass().getName());
            List<Unit> unitList = new ArrayList<>(units);
            units.insertAfter(localInitAssign, unitList.get(getAtStmtNumber(units) - 1));
            SootMethod initMethod = getInitMethod(calleeMethod.getDeclaringClass());
            Unit initUnit = specialCallStatement(localModel, initMethod, fillParameters(currentMethod, body, initMethod));
            units.insertAfter(initUnit, localInitAssign);
        } else {
            localModel = existLocal;
        }
        return localModel;
    }

    public Integer getAtStmtNumber(PatchingChain<Unit> units) {
        int atNumber = 0;
        for (Unit unit : units) {
            if (unit.toString().contains(":= @")) {
                atNumber++;
            } else {
                break;
            }
        }
        return atNumber;
    }

    private Value getLocalFromSingletonFactory(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        SootClass singletonFactory = Scene.v().getSootClass("synthetic.method.SingletonFactory");
        Local paramRef = newLocalVar(sootClass.getShortName().toLowerCase(), sootClass.getName());
        Local existLocal = checkLocalExit(body, paramRef);
        if (existLocal != null) {
            return existLocal;
        }
        body.getLocals().add(paramRef);
        Value returnValue;
        if (singletonFactory.declaresMethod(sootClass.getName() + " get" + sootClass.getShortName() + "()")) {
            returnValue = createStaticInvokeExpr(singletonFactory.getMethod(sootClass.getName() + " get" + sootClass.getShortName() + "()"));
        } else {
            try {
                returnValue = createStaticInvokeExpr(singletonFactory.getMethodByName("get" + sootClass.getShortName()));
            } catch (Exception e) {
                return NullConstant.v();
            }
        }
        List<Unit> unitList = new ArrayList<>(units);
        units.insertAfter(createAssignStmt(paramRef, returnValue), unitList.get(getAtStmtNumber(units) - 1));
        return paramRef;
    }

    private Value getLocalForServlet(JimpleBody body, String sootClassName, SootClass sootClass) {
        for (Local paramLocal : body.getLocals()) {
            if (paramLocal.getType().toString().contains(sootClassName)) {
                return paramLocal;
            }
        }
        return mockParamOfHttpServlet(body, body.getUnits(), sootClass);
    }

    private Value getLocalForSession(JimpleBody body, String sootClassName, SootClass sootClass) {
        for (Local paramLocal : body.getLocals()) {
            if (paramLocal.getType().toString().contains(sootClassName)) {
                return paramLocal;
            }
        }
        return mockParamOfHttpSession(body, body.getUnits(), sootClass);
    }

    public Local mockParamOfHttpServlet(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        return new MockObjectImpl().mockHttpServlet(body, units, sootClass);
    }

    public Value mockParamOfString(SootMethod method, JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        String methodName = "getString";
        Local paramRef = addLocalVar(methodName, sootClass.getName(), body);
        SootMethod stringMethod = genCustomMethod(method.getDeclaringClass(), methodName, new ArrayList<>(), sootClass.getType());
        SootMethod existMethod = checkMethodExist(method.getDeclaringClass(), stringMethod);
        if (existMethod == null) {
            method.getDeclaringClass().addMethod(stringMethod);
        } else {
            stringMethod = existMethod;
        }
        List<Unit> unitList = new ArrayList<>(units);
        Value invokeStringMethod = createVirtualInvokeExpr(body.getThisLocal(), stringMethod);
        units.insertAfter(createAssignStmt(paramRef, invokeStringMethod), unitList.get(getAtStmtNumber(units) - 1));
        return paramRef;
    }

    public Local mockParamOfHttpSession(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        return new MockObjectImpl().mockHttpSession(body, units, sootClass);
    }

    public Local mockParamOfFilterChain(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass) {
        return new MockObjectImpl().mockFilterChain(body, units, sootClass);
    }

    private Value getLocalForJoinPoint(JimpleBody body, SootClass sootClass) {
        for (Local local : body.getLocals()) {
            if (local.getType().equals(sootClass.getType())) {
                return local;
            } else if ((local.getType() instanceof RefType)
                    && SootUtil.getSuperClassesAndInterfaces(((RefType) local.getType()).getSootClass()).contains(sootClass)) {
                return local;
            }
        }
        return NullConstant.v();
    }

    private SootMethod checkMethodExist(SootClass sootClass, SootMethod sootMethod) {
        for (SootMethod method : sootClass.getMethods()) {
            if (method.getSubSignature().equals(sootMethod.getSubSignature())) {
                return method;
            }
        }
        return null;
    }

    private Value mockParamOfBean(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass, SootMethod toCall) {
        SootClass singletonFactory = Scene.v().getSootClass("synthetic.method.SingletonFactory");
        if (singletonFactory.declaresMethodByName("get" + sootClass.getShortName())
                && alreadyInitBean.contains("get" + sootClass.getShortName())) {
            return getLocalFromSingletonFactory(body, units, sootClass);
        } else if (beansAndMethods.containsKey(sootClass)) {
            SootMethod paramMethod = beansAndMethods.get(sootClass);
            SootClass paramClass = paramMethod.getDeclaringClass();
            Local paramRef = newLocalVar(SootUtil.lowerFirst(paramClass.getShortName()), paramClass.getName());
            Local exitLocal = checkLocalExit(body, paramRef);
            if (exitLocal == null) {
                body.getLocals().add(paramRef);
                Unit lastUnit = units.getLast();
                if (lastUnit instanceof ReturnStmt || lastUnit instanceof ReturnVoidStmt) {
                    units.insertBefore(createAssignStmt(paramRef, paramClass.getName()), lastUnit);
                } else {
                    units.add(createAssignStmt(paramRef, paramClass.getName()));
                }
                SootMethod initMethod = getInitMethod(paramClass);
                List<Value> params = new ArrayList<>();
                for (Type ignored : initMethod.getParameterTypes()) {
                    params.add(NullConstant.v());
                }
                if (lastUnit instanceof ReturnStmt || lastUnit instanceof ReturnVoidStmt) {
                    units.insertBefore(specialCallStatement(paramRef, initMethod, params), lastUnit);
                } else {
                    units.add(specialCallStatement(paramRef, initMethod, params));
                }
            } else {
                paramRef = exitLocal;
            }
            Local tmpRef = newLocalVar("tmp_" + SootUtil.lowerFirst(sootClass.getShortName()), paramMethod.getReturnType());
            Local exitTmpRef = checkLocalExit(body, tmpRef);
            if (exitTmpRef == null) {
                Unit lastUnit = units.getLast();
                body.getLocals().add(tmpRef);
                if (paramMethod.isStatic()) {
                    if (lastUnit instanceof ReturnStmt || lastUnit instanceof ReturnVoidStmt) {
                        units.insertBefore(createAssignStmt(tmpRef, createStaticInvokeExpr(paramMethod, fillParameters(null, body, paramMethod))), lastUnit);
                    } else {
                        units.add(createAssignStmt(tmpRef, createStaticInvokeExpr(paramMethod, fillParameters(null, body, paramMethod))));
                    }
                } else {
                    if (lastUnit instanceof ReturnStmt || lastUnit instanceof ReturnVoidStmt) {
                        units.insertBefore(createAssignStmt(tmpRef, createVirtualInvokeExpr(paramRef, paramMethod, fillParameters(null, body, paramMethod))), lastUnit);
                    } else {
                        units.add(createAssignStmt(tmpRef, createVirtualInvokeExpr(paramRef, paramMethod, fillParameters(null, body, paramMethod))));
                    }
                }
                if (!toCall.getName().startsWith("set")) {
                    SootMethod sootMethod = null;
                    try {
                        sootMethod = singletonFactory.getMethodByName("set" + sootClass.getShortName());
                    } catch (Exception e) {
                        for (SootMethod method : singletonFactory.getMethods()) {
                            if (method.getName().equals("set" + sootClass.getShortName())) {
                                sootMethod = method;
                            }
                        }
                    }
                    if (lastUnit instanceof ReturnStmt || lastUnit instanceof ReturnVoidStmt) {
                        units.insertBefore(staticCallStatement(sootMethod, tmpRef), lastUnit);
                    } else {
                        units.add(staticCallStatement(sootMethod, tmpRef));
                    }
                    alreadyInitBean.add("set" + sootClass.getShortName());
                }
            } else {
                tmpRef = exitTmpRef;
            }
            return tmpRef;
        }
        return new MockObjectImpl().mockBean(body, units, sootClass, toCall);
    }

    public boolean isBaseTypes(Type target) {
        return target instanceof LongType
                || target instanceof IntType
                || target instanceof DoubleType
                || target instanceof FloatType
                || target instanceof ArrayType
                || target instanceof BooleanType;
    }

    public void initFiled(Body initBody, Local thisLocal, SootField field, UnitPatchingChain units) {
        Type filedType = field.getType();
        Value initValue = null;
        if (filedType instanceof IntType) {
            initValue = IntConstant.v(0);
        } else if (filedType instanceof LongType) {
            initValue = LongConstant.v(0);
        } else if (filedType instanceof DoubleType) {
            initValue = DoubleConstant.v(0);
        } else if (filedType instanceof FloatType) {
            initValue = FloatConstant.v(0);
        } else if (filedType instanceof BooleanType) {
            initValue = IntConstant.v(0);
        } else if (filedType instanceof ByteType) {
            initValue = IntConstant.v(0);
        } else if (filedType instanceof RefType && filedType.toString().equals("java.lang.String")) {
            initValue = StringConstant.v("");
        } else if (filedType instanceof ArrayType) {
            initValue = NullConstant.v();
        } else if (filedType instanceof RefType && !filterBaseClass(filedType)) {
            SootClass refClass = ((RefType) filedType).getSootClass();
            if (refClass.declaresMethod("void <init>()")
                    && (checkLocalExist(initBody, field.getName()) == null)) {
                Local tmpRef = addLocalVar(field.getName(), filedType, initBody);
                units.insertBefore(createAssignStmt(tmpRef, refClass.getName()), units.getLast());
                units.insertBefore(specialCallStatement(tmpRef,
                        refClass.getMethod("void <init>()")), units.getLast());
                initValue = tmpRef;
            }
        }
        if (initValue == null) {
            initValue = NullConstant.v();
        }
        Stmt stmt = Jimple.v().newAssignStmt(createInstanceFieldRef(thisLocal, field.makeRef()), initValue);
        units.insertBefore(stmt, units.getLast());
    }

    public SootMethod getInitMethod(SootClass sootClass) {
        if (sootClass.declaresMethod("void <init>()")) {
            return sootClass.getMethod("void <init>()");
        } else {
            for (SootMethod initMethod : sootClass.getMethods()) {
                if (initMethod.getName().contains("<init>")) {
                    return initMethod;
                }
            }
        }
        return sootClass.getMethodByName("<init>");
    }

    public boolean filterBaseClass(Type type) {
        switch (type.toString()) {
            case "java.lang.Integer":
            case "java.lang.Integer[]":
            case "java.lang.Long":
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.lang.Boolean":
            case "java.lang.Byte":
            case "java.lang.String":
                return true;
            default:
                return false;
        }
    }

    public boolean filterCollectClass(Type type) {
        switch (type.toString()) {
            case "org.springframework.data.domain.Page":
            case "java.util.List":
                return true;
            default:
                return false;
        }
    }
}
