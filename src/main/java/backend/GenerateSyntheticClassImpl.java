package backend;

import analysis.CreateEdge;
import bean.ConstructorArgBean;
import bean.DBColumnBean;
import bean.DBMethodBean;
import soot.*;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JIdentityStmt;
import soot.tagkit.SignatureTag;
import soot.tagkit.Tag;
import utils.DBUtil;
import utils.JimpleUtil;
import utils.SootUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static analysis.CreateEdge.needInitBeans;
import static utils.DBUtil.mapperAndResultMap;
import static utils.StringConstantUtil.OBJECT_CLASS;

public class GenerateSyntheticClassImpl implements GenerateSyntheticClass {
    public static final Set<String> alreadyInitBean = new HashSet<>();
    private final JimpleUtil jimpleUtil = new JimpleUtil();
    private static final Map<String, SootClass> syntheticMethodImpls = new HashMap<>();

    @Override
    public SootClass generateJoinPointImpl(SootClass abstractClass) {
        SootClass customImplClass;
        String implClassName = "synthetic.method." + abstractClass.getShortName() + "Impl";
        if (syntheticMethodImpls.containsKey(implClassName)) {
            customImplClass = syntheticMethodImpls.get(implClassName);
        } else {
            customImplClass = createSubClass(implClassName, abstractClass, Scene.v().getSootClass(OBJECT_CLASS));
            customImplClass.addInterface(Scene.v().getSootClass("org.aspectj.lang.JoinPoint"));
            Scene.v().addClass(customImplClass);
            customImplClass.setApplicationClass();
            SootField field = new SootField("args", ArrayType.v(RefType.v(OBJECT_CLASS), 1));
            customImplClass.addField(field);
            SootMethod initMethod = jimpleUtil.genDefaultConstructor(customImplClass);
            customImplClass.addMethod(initMethod);

            for (SootClass anInterface : customImplClass.getInterfaces()) {
                implCommonMethod(customImplClass, anInterface);
            }
            for (SootClass abstractClassInterface : abstractClass.getInterfaces()) {
                implCommonMethod(customImplClass, abstractClassInterface);
            }
            customImplClass.addMethod(jimpleUtil.genCustomMethod(customImplClass,
                    "setArgs_synthetic",
                    Arrays.asList(new Type[]{ArrayType.v(RefType.v(OBJECT_CLASS), 1)}),
                    VoidType.v()));
            syntheticMethodImpls.put(implClassName, customImplClass);
        }
        return customImplClass;
    }

    @Override
    public SootClass generateMapperImpl(SootClass interfaceClass, DBMethodBean dbMethodBean) {
        SootClass mapperImplClass;
        String implClassName = "synthetic.method." + interfaceClass.getShortName() + "Impl";
        if (syntheticMethodImpls.containsKey(implClassName)) {
            mapperImplClass = syntheticMethodImpls.get(implClassName);
        } else {
            mapperImplClass = createSubClass(implClassName, interfaceClass, Scene.v().getSootClass(OBJECT_CLASS));
            Scene.v().addClass(mapperImplClass);
            mapperImplClass.setApplicationClass();
            SootMethod initMethod = jimpleUtil.genDefaultConstructor(mapperImplClass);
            mapperImplClass.addMethod(initMethod);
            syntheticMethodImpls.put(implClassName, mapperImplClass);
        }
        implSQLMethod(mapperImplClass, interfaceClass, dbMethodBean);
        return mapperImplClass;
    }

    @Override
    public SootClass generateProxy(SootClass targetSootClass, String proxyClassName, List<Type> additionalParam) {
        SootClass proxyClass;
        if (syntheticMethodImpls.containsKey(proxyClassName)) {
            proxyClass = syntheticMethodImpls.get(proxyClassName);
        } else {
            boolean isInterface = targetSootClass.isInterface();
            if (isInterface) {
                proxyClass = createSubClass(proxyClassName, targetSootClass, Scene.v().getSootClass(OBJECT_CLASS));
            } else {
                proxyClass = createSubClass(proxyClassName, null, targetSootClass);
            }
            Scene.v().addClass(proxyClass);
            SootField field = new SootField("target", targetSootClass.getType());
            proxyClass.addField(field);
            proxyClass.setApplicationClass();
            SootMethod initMethod;
            if (CreateEdge.prototypeComponents.contains(proxyClass)) {
                initMethod = jimpleUtil.genDefaultConstructor(proxyClass, field, false);
            } else {
                initMethod = jimpleUtil.genDefaultConstructor(proxyClass, field, true);
            }

            proxyClass.addMethod(initMethod);
            if (isInterface) {
                implCommonMethod(proxyClass, targetSootClass);
            } else {
                extendCommonMethod(proxyClass, targetSootClass, additionalParam);
            }
            syntheticMethodImpls.put(proxyClassName, proxyClass);
        }
        return proxyClass;
    }

    @Override
    public void generateSingletonBeanFactory(Set<SootClass> beans, Set<SootClass> singleBeans, Map<String, List<ConstructorArgBean>> collect) {
        //declare singletonFactory
        SootClass singletonFactory = createSubClass("synthetic.method.SingletonFactory",
                null, Scene.v().getSootClass(OBJECT_CLASS));
        Scene.v().addClass(singletonFactory);
        singletonFactory.setApplicationClass();
        // process fields in singletonFactory
        Set<SootField> fields = new HashSet<>();
        for (SootClass bean : beans) {
            String beanName = bean.getShortName();
            if (singletonFactory.declaresMethod(bean.getType() + " get" + beanName + "()")) {
                continue;
            }
            SootField field = new SootField(SootUtil.lowerFirst(beanName), bean.getType(), Modifier.PUBLIC + Modifier.STATIC);
            singletonFactory.addField(field);
            // gen static get method
            SootMethod newGetMethod = jimpleUtil.genStaticCustomMethod("get" + beanName, null, bean.getType(), field);
            singletonFactory.addMethod(newGetMethod);
            if (!singleBeans.contains(bean)) {
                alreadyInitBean.add("get" + beanName);
                fields.add(field);
            } else {
                // gen static set method
                SootMethod newSetMethod = jimpleUtil.genStaticCustomMethod("set" + beanName,
                        Collections.singletonList(bean.getType()), VoidType.v(), field);
                singletonFactory.addMethod(newSetMethod);
                needInitBeans.add(newSetMethod.getSubSignature());
            }
            if (bean.getInterfaces().size() == 1) {
                String interfaceBeanMethodName = "get" + bean.getInterfaces().getFirst().getShortName();
                if (!singletonFactory.declaresMethod(bean.getType() + " " + interfaceBeanMethodName + "()")) {
                    singletonFactory.addMethod(jimpleUtil.genStaticCustomMethod(interfaceBeanMethodName,
                            null, bean.getType(), field));
                }
            }
        }
        singletonFactory.addMethod(jimpleUtil.genDefaultConstructor(singletonFactory, null, false));
        singletonFactory.addMethod(jimpleUtil.genDefaultClinit(fields, collect));
    }

    @Override
    public void generateDataTableClass(String tableName, DBMethodBean dbMethodBean) {
        SootClass tableFactory;
        if (Scene.v().containsClass(tableName)) {
            tableFactory = Scene.v().getSootClass(tableName);
        } else {
            tableFactory = createSubClass(tableName, null, Scene.v().getSootClass(OBJECT_CLASS));
            Scene.v().addClass(tableFactory);
            tableFactory.setApplicationClass();
            SootField selfField = new SootField("instance", tableFactory.getType(), Modifier.PRIVATE + Modifier.STATIC);
            tableFactory.addField(selfField);
            SootMethod newGetMethod = jimpleUtil.genStaticCustomMethod("getInstance", null, selfField.getType(), selfField);
            tableFactory.addMethod(newGetMethod);
            tableFactory.addMethod(jimpleUtil.genDefaultConstructor(tableFactory, null, false));
            tableFactory.addMethod(jimpleUtil.genDefaultClinit(Collections.singleton(selfField), new HashMap<>()));
        }

        Body initBody = tableFactory.getMethodByName("<init>").retrieveActiveBody();
        Local thisLocal = initBody.getThisLocal();
        UnitPatchingChain units = initBody.getUnits();
        for (SootField field : getDataTableFields(dbMethodBean)) {
            if (tableFactory.declaresMethodByName("get" + field.getName())) {
                continue;
            }
            tableFactory.addField(field);
            jimpleUtil.initFiled(initBody, thisLocal, field, units);
            SootMethod newGetMethod = jimpleUtil.genCustomMethodWithField("get" + field.getName(),
                    tableFactory, new ArrayList<>(), field.getType(), field);
            tableFactory.addMethod(newGetMethod);
            SootMethod newSetMethod = jimpleUtil.genCustomMethodWithField("set" + field.getName(),
                    tableFactory, Collections.singletonList(field.getType()), VoidType.v(), field);
            tableFactory.addMethod(newSetMethod);
        }
    }

    private List<SootField> getDataTableFields(DBMethodBean dbMethodBean) {
        List<SootField> sootFields = new ArrayList<>();
        SootMethod mapperMethod = dbMethodBean.getSootMethod();
        Map<String, String> foreachMap = mapperAndResultMap.get(mapperMethod.getName());
        List<Type> paramTypes = mapperMethod.getParameterTypes();
        Type returnType = mapperMethod.getReturnType();
        new DBUtil().findRealClass(dbMethodBean, dbMethodBean.getSootClass(), mapperMethod);

        if (dbMethodBean.getMethodType().equals("select")) {
            if (returnType instanceof RefType && !jimpleUtil.filterBaseClass(returnType) && !jimpleUtil.filterCollectClass(returnType)) {
                SootClass returnClass = ((RefType) returnType).getSootClass();
                if ((returnClass.isAbstract() || returnClass.isInterface()) && dbMethodBean.getResultClass() != null) {
                    if (dbMethodBean.getResultClass().getFields().size() == 0) {
                        dbMethodBean.setResultClass(dbMethodBean.getGenerics());
                    }
                    returnClass = dbMethodBean.getResultClass();
                }
                List<DBColumnBean> extDBColumnBeanList = new ArrayList<>();
                for (DBColumnBean dbColumnBean : dbMethodBean.getDbColumnBeanList()) {
                    if (dbColumnBean.getParamName().contains("*")) {
                        processReturnAllForSelectMethod(sootFields, returnClass, extDBColumnBeanList);
                        break;
                    } else {
                        formatParaNameAndValueName(dbColumnBean);
                        SootField field = SootUtil.findSpecialFieldIgnoreCase(dbColumnBean.getValueName(), returnClass);
                        String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                        if (field != null) {
                            sootFields.add(new SootField(fieldName, field.getType(), Modifier.PRIVATE));
                        }
                    }
                }
                if (extDBColumnBeanList.size() > 0) {
                    dbMethodBean.getDbColumnBeanList().clear();
                    dbMethodBean.getDbColumnBeanList().addAll(extDBColumnBeanList);
                }
            } else if (returnType instanceof RefType && jimpleUtil.filterCollectClass(returnType)) {
                sootFields.addAll(processCollectType(dbMethodBean));
            } else if (dbMethodBean.getDbColumnBeanList().size() > 0) {
                sootFields.add(new SootField(dbMethodBean.getDbColumnBeanList().get(0).getParamName().replace("_", "")
                        .toLowerCase(), returnType, Modifier.PRIVATE));
            } else {
                System.err.println(dbMethodBean);
            }
        } else if (dbMethodBean.getMethodType().equals("insert") || dbMethodBean.getMethodType().equals("update")) {
            for (DBColumnBean dbColumnBean : dbMethodBean.getDbColumnBeanList()) {
                processTableField(dbMethodBean, sootFields, mapperMethod, foreachMap, paramTypes, dbColumnBean);
            }
        }

        for (DBColumnBean dbColumnBean : dbMethodBean.getWhereConditionList()) {
            processTableField(dbMethodBean, sootFields, mapperMethod, foreachMap, paramTypes, dbColumnBean);
        }

        for (DBColumnBean sqlParamName : dbMethodBean.getSqlParamNames()) {
            for (int i = 0; i < paramTypes.size(); i++) {
                Type paramType = paramTypes.get(i);
                if (paramType.toString().equals(OBJECT_CLASS)) {
                    paramType = dbMethodBean.getGenerics().getType();
                }
                if (paramType instanceof RefType && !jimpleUtil.filterBaseClass(paramType) && !jimpleUtil.filterCollectClass(paramType)) {
                    SootClass paramClass = ((RefType) paramType).getSootClass();
                    if (dbMethodBean.getParamNames() != null && dbMethodBean.getParamNames().size() > 0 && i < dbMethodBean.getParamNames().size()) {
                        String paramName = dbMethodBean.getParamNames().get(i);
                        if (sqlParamName.getValueName().startsWith(paramName + ".")) {
                            sqlParamName.setValueName(sqlParamName.getValueName().replace(paramName + ".", ""));
                        }
                    }
                    if (SootUtil.findSpecialFieldIgnoreCase(sqlParamName.getValueName(), paramClass) != null) {
                        sqlParamName.setParamIndex(i);
                        break;
                    }
                } else {
                    sqlParamName.setParamIndex(i);
                }
            }
            sootFields.add(new SootField(sqlParamName.getValueName().toLowerCase() + "Inject", RefType.v(OBJECT_CLASS), Modifier.PRIVATE));
        }
        return sootFields;
    }

    private void processTableField(DBMethodBean dbMethodBean, List<SootField> sootFields, SootMethod mapperMethod, Map<String, String> foreachMap, List<Type> paramTypes, DBColumnBean dbColumnBean) {
        if (dbColumnBean.getParamIndex() == -1) {
            if (dbColumnBean.getParamName().equals("null") || dbColumnBean.getValueName() == null) {
                return;
            }
            Pattern pattern = Pattern.compile("-?[0-9]+");
            if (pattern.matcher(dbColumnBean.getValueName()).matches()) {
                Type fileType = IntType.v();
                dbColumnBean.setValueType("int");
                String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                sootFields.add(new SootField(fieldName, fileType, Modifier.PRIVATE));
            }
        } else {
            for (int i = 0; i < paramTypes.size(); i++) {
                Type paramType = paramTypes.get(i);
                if (paramType.toString().equals(OBJECT_CLASS)) {
                    paramType = dbMethodBean.getGenerics().getType();
                }
                if (DBUtil.xmlParamClass.containsKey(dbMethodBean.getSootClass().getName() + "." + dbMethodBean.getSootMethod().getName())) {
                    SootClass typeClass = DBUtil.xmlParamClass.get(dbMethodBean.getSootClass().getName() + "." + dbMethodBean.getSootMethod().getName());
                    if (typeClass.getFields() != null && typeClass.getFields().size() != 0) {
                        paramType = typeClass.getType();
                    }
                }
                SootField resultField = null;
                if (paramType instanceof RefType && !jimpleUtil.filterBaseClass(paramType) && !jimpleUtil.filterCollectClass(paramType)) {
                    SootClass paramClass = ((RefType) paramType).getSootClass();
                    if (dbMethodBean.getParamNames() != null && dbMethodBean.getParamNames().size() > 0 && i < dbMethodBean.getParamNames().size()) {
                        String paramName = dbMethodBean.getParamNames().get(i);
                        if (dbColumnBean.getValueName().startsWith(paramName + ".")) {
                            dbColumnBean.setValueName(dbColumnBean.getValueName().replace(paramName + ".", ""));
                        }
                    }
                    SootField field;
                    if (dbColumnBean.getValueName().contains("?")) {
                        field = SootUtil.findSpecialFieldIgnoreCase(dbColumnBean.getParamName(), paramClass);
                    } else {
                        field = SootUtil.findSpecialFieldIgnoreCase(dbColumnBean.getValueName(), paramClass);
                    }
                    if (field != null) {
                        String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                        resultField = new SootField(fieldName, field.getType(), Modifier.PRIVATE);
                        dbColumnBean.setParamIndex(i);
                    }
                } else if (paramType instanceof RefType && jimpleUtil.filterCollectClass(paramType)) {
                    for (Tag tag : mapperMethod.getTags()) {
                        if (tag instanceof SignatureTag) {
                            String listType = ((SignatureTag) tag).getSignature().split("\\)")[0];
                            resultField = processCollectTypeForUpdateAndInsert(listType, dbMethodBean, dbColumnBean, i, foreachMap);
                            break;
                        }
                    }
                } else if (dbMethodBean.getParamNames().size() > 0) {
                    String paramName = dbMethodBean.getParamNames().get(i);
                    if (foreachMap != null && foreachMap.containsKey(paramName)) {
                        paramName = foreachMap.get(paramName);
                    }
                    if (paramName.equalsIgnoreCase(dbColumnBean.getValueName())) {
                        String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                        resultField = new SootField(fieldName, paramType, Modifier.PRIVATE);
                        dbColumnBean.setParamIndex(i);
                    }
                } else {
                    String fieldName = dbColumnBean.getParamName().replaceAll("[_+\\d]", "").toLowerCase();
                    if (fieldName.contains(".")) {
                        fieldName = fieldName.split("\\.")[1];
                    }
                    resultField = new SootField(fieldName, paramType, Modifier.PRIVATE);
                    dbColumnBean.setParamIndex(i);
                }

                if (resultField != null) {
                    sootFields.add(resultField);
                    break;
                }
            }
        }
    }

    private List<SootField> processCollectType(DBMethodBean dbMethodBean) {
        List<SootField> sootFields = new ArrayList<>();
        SootClass realClass = dbMethodBean.getGenerics();
        List<DBColumnBean> extDBColumnBeanList = new ArrayList<>();
        if (dbMethodBean.getDbColumnBeanList().size() == 1 && realClass.isApplicationClass()) {
            dbMethodBean.getDbColumnBeanList().get(0).setParamName("*");
        }
        for (DBColumnBean dbColumnBean : dbMethodBean.getDbColumnBeanList()) {
            if (dbColumnBean.getParamName().contains("*")) {
                processReturnAllForSelectMethod(sootFields, realClass, extDBColumnBeanList);
                break;
            } else if (jimpleUtil.filterBaseClass(realClass.getType())) {
                String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                sootFields.add(new SootField(fieldName, realClass.getType(), Modifier.PRIVATE));
            } else {
                formatParaNameAndValueName(dbColumnBean);
                if (realClass.declaresFieldByName(dbColumnBean.getValueName())) {
                    SootField field = realClass.getFieldByName(dbColumnBean.getValueName());
                    String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                    sootFields.add(new SootField(fieldName, field.getType(), Modifier.PRIVATE));
                }
            }
        }
        if (extDBColumnBeanList.size() > 0) {
            dbMethodBean.getDbColumnBeanList().clear();
            dbMethodBean.getDbColumnBeanList().addAll(extDBColumnBeanList);
        }
        return sootFields;
    }

    private void processReturnAllForSelectMethod(List<SootField> sootFields, SootClass realClass, List<DBColumnBean> extDBColumnBeanList) {
        for (SootField field : realClass.getFields()) {
            if (field.isFinal() && field.isStatic()) {
                continue;
            }
            String paramName = field.getName().replace("_", "").toLowerCase();
            extDBColumnBeanList.add(new DBColumnBean(paramName, field.getName()));
            sootFields.add(new SootField(paramName, field.getType(), Modifier.PRIVATE));
        }
    }

    private void formatParaNameAndValueName(DBColumnBean dbColumnBean) {
        if (dbColumnBean.getValueName() == null) {
            dbColumnBean.setValueName(dbColumnBean.getParamName());
        } else if (dbColumnBean.getParamName() == null) {
            dbColumnBean.setParamName(dbColumnBean.getValueName());
        }
        if (dbColumnBean.getValueName().contains(".")) {
            dbColumnBean.setValueName(dbColumnBean.getValueName().split("\\.")[1].replaceAll("['`]", ""));
        }
        if (dbColumnBean.getParamName().contains(".")) {
            dbColumnBean.setParamName(dbColumnBean.getParamName().split("\\.")[1].replaceAll("['`]", ""));
        }
        if (dbColumnBean.getValueName().contains(" ")) {
            dbColumnBean.setValueName(dbColumnBean.getValueName().split(" ")[1].replaceAll("['`]", ""));
        }
        if (dbColumnBean.getParamName().contains(" ")) {
            dbColumnBean.setParamName(dbColumnBean.getParamName().split(" ")[1].replaceAll("['`]", ""));
        }
    }

    private SootField processCollectTypeForUpdateAndInsert(String listType, DBMethodBean dbMethodBean, DBColumnBean dbColumnBean, int index, Map<String, String> foreachMap) {
        Pattern pattern = Pattern.compile("<[A-Za-z/;]*>");
        Matcher typeMatcher = pattern.matcher(listType);
        while (typeMatcher.find()) {
            String type = typeMatcher.group(0).trim().replace("/", ".").replaceAll("[<;>]", "").substring(1);
            SootClass realClass = Scene.v().getSootClass(type);
            dbMethodBean.setGenerics(realClass);
            if (jimpleUtil.filterBaseClass(realClass.getType())) {
                if (dbMethodBean.getParamNames().size() > 0) {
                    String paramName = dbMethodBean.getParamNames().get(index);
                    if (foreachMap.containsKey(paramName)) {
                        paramName = foreachMap.get(paramName);
                    }
                    if (!paramName.equalsIgnoreCase(dbColumnBean.getValueName())) {
                        break;
                    }
                }
                String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                dbColumnBean.setParamIndex(index);
                return new SootField(fieldName, realClass.getType(), Modifier.PRIVATE);
            } else {
                if (dbColumnBean.getValueName() == null) {
                    dbColumnBean.setValueName(dbColumnBean.getParamName());
                }
                if (dbColumnBean.getValueName().contains(".")) {
                    dbColumnBean.setValueName(dbColumnBean.getValueName().split("\\.")[1].replaceAll("['`]", ""));
                }
                SootField field = SootUtil.findSpecialFieldIgnoreCase(dbColumnBean.getValueName(), realClass);
                String fieldName = dbColumnBean.getParamName().replace("_", "").toLowerCase();
                if (field != null) {
                    return new SootField(fieldName, field.getType(), Modifier.PRIVATE);
                }
            }
        }
        return null;
    }

    @Override
    public SootClass generateHttpServlet(SootClass abstractClass) {
        SootClass customImplClass;
        String implClassName = "synthetic.method." + abstractClass.getShortName() + "Impl";
        if (syntheticMethodImpls.containsKey(implClassName)) {
            customImplClass = syntheticMethodImpls.get(implClassName);
        } else {
            customImplClass = createSubClass(implClassName, abstractClass, Scene.v().getSootClass(OBJECT_CLASS));
            Scene.v().addClass(customImplClass);
            customImplClass.setApplicationClass();
            SootMethod initMethod = jimpleUtil.genDefaultConstructor(customImplClass);
            customImplClass.addMethod(initMethod);
            implCommonMethod(customImplClass, abstractClass);
            syntheticMethodImpls.put(implClassName, customImplClass);
        }
        return customImplClass;
    }

    @Override
    public SootClass generateHttpSession(SootClass abstractClass) {
        SootClass customImplClass;
        String implClassName = "synthetic.method." + abstractClass.getShortName() + "Impl";
        if (syntheticMethodImpls.containsKey(implClassName)) {
            customImplClass = syntheticMethodImpls.get(implClassName);
        } else {
            customImplClass = createSubClass(implClassName, abstractClass, Scene.v().getSootClass(OBJECT_CLASS));
            Scene.v().addClass(customImplClass);
            customImplClass.setApplicationClass();
            SootField field = new SootField("map", RefType.v("java.util.HashMap"), 1);
            customImplClass.addField(field);

            SootMethod initMethod = jimpleUtil.genDefaultConstructor(customImplClass, field, false);
            customImplClass.addMethod(initMethod);
            implCommonMethod(customImplClass, abstractClass);
            syntheticMethodImpls.put(implClassName, customImplClass);
        }
        return customImplClass;
    }

    @Override
    public SootClass generateFilterChain(SootClass abstractClass) {
        SootClass customImplClass;
        String implClassName = "synthetic.method." + abstractClass.getShortName() + "Impl";
        if (syntheticMethodImpls.containsKey(implClassName)) {
            customImplClass = syntheticMethodImpls.get(implClassName);
        } else {
            customImplClass = createSubClass(implClassName, abstractClass, Scene.v().getSootClass(OBJECT_CLASS));
            Scene.v().addClass(customImplClass);
            customImplClass.setApplicationClass();
            SootMethod initMethod = jimpleUtil.genDefaultConstructor(customImplClass);
            customImplClass.addMethod(initMethod);
            implCommonMethod(customImplClass, abstractClass);
            syntheticMethodImpls.put(implClassName, customImplClass);
        }
        return customImplClass;
    }


    public SootClass createSubClass(String implClassName, SootClass interfaceClass, SootClass superClass) {
        SootClass customImplClass = new SootClass(implClassName);
        customImplClass.setResolvingLevel(SootClass.BODIES);
        if (interfaceClass != null) {
            customImplClass.addInterface(interfaceClass);
        }
        customImplClass.setModifiers(Modifier.PUBLIC);
        customImplClass.setSuperclass(superClass);
        return customImplClass;
    }

    public void implCommonMethod(SootClass customImplClass, SootClass interfaceClass) {
        for (SootMethod method : interfaceClass.getMethods()) {
            if (!customImplClass.declaresMethod(method.getName(), method.getParameterTypes(), method.getReturnType())) {
                customImplClass.addMethod(jimpleUtil.genCustomMethod(customImplClass,
                        method.getName(),
                        method.getParameterTypes(),
                        method.getReturnType()));
            }
        }
        for (SootClass superInterface : interfaceClass.getInterfaces()) {
            implCommonMethod(customImplClass, superInterface);
        }
    }


    public void implSQLMethod(SootClass customImplClass, SootClass interfaceClass, DBMethodBean dbMethodBean) {
        SootMethod method = dbMethodBean.getSootMethod();
        if (dbMethodBean.getTableName() != null && !dbMethodBean.getTableName().equals("")) {
            String className = "synthetic.method.datatable." + dbMethodBean.getTableName().replace("_", "").toUpperCase();
            generateDataTableClass(className, dbMethodBean);
            dbMethodBean.setTableClassName(className);
        }
        if (!customImplClass.declaresMethod(method.getName(), method.getParameterTypes(), method.getReturnType())) {
            customImplClass.addMethod(jimpleUtil.genSQLMethod(customImplClass, method.getName(), method.getParameterTypes(), method.getReturnType(), dbMethodBean));
        }
        // for (SootClass superInterface : interfaceClass.getInterfaces()) {
        //     implSQLMethod(customImplClass, superInterface, dbMethodBean);
        // }
    }

    public void extendCommonMethod(SootClass customSubClass, SootClass superClass, List<Type> additionalParam) {
        for (SootMethod superMethod : superClass.getMethods()) {
            if (superMethod.isStatic() || superMethod.isPrivate()
                    || superMethod.isFinal() || superMethod.getName().contains("<init>")
                    || superMethod.getName().contains("<clinit>")) {
                continue;
            }
            List<Type> oriParamList = superMethod.getParameterTypes();
            List<Type> finalParamList = new ArrayList<>(oriParamList);
            Set<Type> paramSet = new HashSet<>(oriParamList);
            int addNumber = 0;
            for (Type type : additionalParam) {
                if (!paramSet.contains(type)) {
                    finalParamList.add(type);
                    addNumber++;
                }
            }
            SootMethod subMethod = new SootMethod(superMethod.getName(),
                    finalParamList,
                    superMethod.getReturnType(),
                    superMethod.getModifiers());
            customSubClass.addMethod(subMethod);
            JimpleBody subMethodBody = Jimple.v().newBody(subMethod);
            JimpleBody cloneBody = (JimpleBody) superMethod.retrieveActiveBody().clone();

            subMethodBody.getLocals().addAll(cloneBody.getParameterLocals());
            PatchingChain<Unit> units = subMethodBody.getUnits();
            units.addAll(cloneBody.getUnits());
            units.removeIf(unit -> !(unit instanceof JIdentityStmt && unit.toString().contains("@parameter")));

            for (int i = addNumber; 0 < i; i--) {
                RefType param = (RefType) finalParamList.get(finalParamList.size() - i);
                Local paramLocal = jimpleUtil.addLocalVar(param.getSootClass().getShortName(), param, subMethodBody);
                jimpleUtil.createIdentityStmt(paramLocal, jimpleUtil.createParamRef(param, finalParamList.size() - i), units);
            }

            Local thisRef = jimpleUtil.addLocalVar("this", customSubClass.getType(), subMethodBody);
            jimpleUtil.createIdentityStmt(thisRef, jimpleUtil.createThisRef(customSubClass.getType()), units);
            Local returnRef = null;
            if (!(subMethod.getReturnType() instanceof VoidType)) {
                returnRef = jimpleUtil.addLocalVar("returnRef", subMethod.getReturnType(), subMethodBody);
            }
            for (SootField field : customSubClass.getFields()) {
                Local tmpRef = jimpleUtil.addLocalVar("localTarget", field.getType(), subMethodBody);
                jimpleUtil.createAssignStmt(tmpRef, jimpleUtil.createInstanceFieldRef(thisRef, field.makeRef()), units);
                List<Local> actualParamList = new ArrayList<>();
                for (int i = 0; i < superMethod.getParameterCount(); i++) {
                    actualParamList.add(subMethodBody.getParameterLocal(i));
                }
                if (!(superMethod.getReturnType() instanceof VoidType)) {
                    Value returnValue = jimpleUtil.createVirtualInvokeExpr(tmpRef, superMethod, actualParamList);
                    jimpleUtil.createAssignStmt(returnRef, returnValue, units);
                } else {
                    units.add(jimpleUtil.virtualCallStatement(tmpRef, superMethod.toString(), actualParamList));
                }
            }

            if (returnRef != null) {
                jimpleUtil.addCommonReturnStmt(returnRef, units);
            } else {
                jimpleUtil.addVoidReturnStmt(units);
            }
            subMethod.setActiveBody(subMethodBody);
        }
    }
}
