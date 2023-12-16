package frontend;

import analysis.CreateEdge;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.tagkit.SignatureTag;
import soot.tagkit.Tag;
import transformer.BasicCoRTransformer;
import utils.JimpleUtil;
import utils.SootUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.StringConstantUtil.*;


public class IOCParser {
    private final AnnotationAnalysis annotationAnalysis = new AnnotationAnalysis();
    private final JimpleUtil jimpleUtil = new JimpleUtil();
    private final Map<SootClass, String> initMap = new HashMap<>();
    private final Map<SootClass, SootClass> potentialImpl = new HashMap<>();
    private final SootClass singletonFactory = Scene.v().getSootClass("synthetic.method.SingletonFactory");

    public void getIOCObject(SootClass sootClass, Collection<SootMethod> allBeans) {
        for (SootMethod method : sootClass.getMethods()) {
            List<Type> paramOfAutoWiredMethod = annotationAnalysis.getParamOfAutoWiredMethod(method);
            if (paramOfAutoWiredMethod != null) {
                AnnotationAnalysis.autoMethodParams.addAll(paramOfAutoWiredMethod);
            }
        }
        SootMethod initMethod = jimpleUtil.getInitMethod(sootClass);
        SootClass realMapper = findGenericsMapper(sootClass);
        for (SootField classField : getAllFields(sootClass)) {
            SootField field = this.annotationAnalysis.getFieldWithSpecialAnnos(classField, initMethod);
            if (field != null) {
                String fieldName = ((RefType) field.getType()).getSootClass().getName();
                if (fieldName.equals("com.baomidou.mybatisplus.core.mapper.BaseMapper") && realMapper != sootClass) {
                    fieldName = realMapper.getName();
                    classField.setModifiers(Modifier.PUBLIC);
                }
                SootClass aClass = CreateEdge.interfaceToBeans.getOrDefault(fieldName, null);
                String vtype = field.getType().toString();
                SootFieldRef sootFieldRef = field.makeRef();
                if (aClass != null && !aClass.isInterface()) {
                    SootClass beanClass = BasicCoRTransformer.proxyClassMap.getOrDefault(aClass.getName(), aClass);
                    if (beanClass != aClass || CreateEdge.prototypeComponents.contains(aClass)) {
                        initIOCObjectByPrototype(sootFieldRef, initMethod, vtype, beanClass.getType().toString(), mapInitMethod(beanClass, initMap));
                    } else {
                        initIOCObjectBySingleton(sootFieldRef, initMethod, vtype, aClass);
                    }
                    continue;
                } else {
                    SootClass fieldClass = ((RefType) field.getType()).getSootClass();
                    if (fieldClass.isPhantom() || filterBaseClass(fieldClass)) {
                        continue;
                    } else if (!fieldClass.isInterface() && !fieldClass.isAbstract()) {
                        initIOCObjectByPrototype(sootFieldRef, initMethod, vtype, fieldClass.getType().toString(), mapInitMethod(fieldClass, initMap));
                        continue;
                    }
                    SootClass hierarchyClass = null;
                    if (potentialImpl.containsKey(fieldClass)) {
                        hierarchyClass = potentialImpl.get(fieldClass);
                    } else {

                        List<SootClass> hierarchyClasses = SootUtil.getSubClassOrImplementClass(fieldClass);
                        if (hierarchyClasses.size() > 0) {
                            hierarchyClass = hierarchyClasses.get(0);
                            potentialImpl.put(fieldClass, hierarchyClass);
                        }
                    }
                    if (hierarchyClass != null) {
                        initIOCObjectByPrototype(sootFieldRef, initMethod, vtype, hierarchyClass.getType().toString(), mapInitMethod(hierarchyClass, initMap));
                        continue;
                    } else {
                        System.out.println("can't find this bean: " + fieldClass.getName() + " in " + sootClass);
                    }
                }

                for (SootMethod bean : allBeans) {
                    RefType returnType = null;
                    SootClass returnClass = null;
                    if (!(bean.getReturnType() instanceof VoidType) && (field.getType().equals(bean.getReturnType())
                            || ((RefType) bean.getReturnType()).getSootClass().getInterfaces()
                            .contains(((RefType) bean.getReturnType()).getSootClass()))) {
                        for (Unit unit : bean.retrieveActiveBody().getUnits()) {
                            if (unit instanceof JReturnStmt) {
                                returnType = (RefType) ((JReturnStmt) unit).getOpBox().getValue().getType();
                                returnClass = returnType.getSootClass();
                                break;
                            }
                        }
                        if (returnClass == null
                                || returnClass.isInterface() || returnClass.isAbstract()
                                || returnClass.getMethodUnsafe("void <init>()") == null) {
                            initIOCObjectByPrototype(sootFieldRef, initMethod, vtype, bean.getDeclaringClass().toString(),
                                    bean.getDeclaringClass().getMethodByNameUnsafe("<init>").toString(),
                                    bean.toString());
                            break;
                        }
                        initIOCObjectByPrototype(sootFieldRef, initMethod, vtype, returnType.toString(), returnClass.getMethodUnsafe("void <init>()").toString());
                        break;
                    }
                }
            }
        }
    }

    private SootClass findGenericsMapper(SootClass sootClass) {
        SootClass realMapper = sootClass;
        if (findSpecialSuperClass(sootClass.getSuperclass())) {
            for (Tag tag : sootClass.getTags()) {
                if (tag instanceof SignatureTag) {
                    String listType = ((SignatureTag) tag).getSignature().split("\\)")[0];
                    Pattern pattern = Pattern.compile("/ServiceImpl<[A-Za-z/;]*>");
                    Matcher typeMatcher = pattern.matcher(listType);
                    while (typeMatcher.find()) {
                        String[] typeArray = typeMatcher.group(0).trim().replace("/ServiceImpl", "").replace("/", ".").split(";");
                        String type = typeArray[0].replaceAll("[<;>]", "").substring(1);
                        realMapper = Scene.v().getSootClass(type);
                    }
                    break;
                }
            }
        }
        return realMapper;
    }

    private List<SootField> getAllFields(SootClass sootClass) {
        List<SootField> sootFieldList = new ArrayList<>(sootClass.getFields());
        if (findSpecialSuperClass(sootClass.getSuperclass())) {
            sootFieldList.addAll(getAllFields(sootClass.getSuperclass()));
        }
        return sootFieldList;
    }

    private boolean findSpecialSuperClass(SootClass sootClass) {
        if (sootClass.isPhantom()) {
            return false;
        }
        switch (sootClass.getName()) {
            case OBJECT_CLASS:
                return false;
            case "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl":
                return true;
            default:
                return findSpecialSuperClass(sootClass.getSuperclass());
        }
    }

    private boolean filterBaseClass(SootClass sc) {
        switch (sc.getName()) {
            // case "java.lang.Integer":
            // case "java.lang.Long":
            // case "java.lang.Float":
            // case "java.lang.Double":
            // case "java.lang.Boolean":
            // case "java.lang.Byte":
            case "java.lang.String":
                return true;
            default:
                return false;
        }
    }

    private String mapInitMethod(SootClass sootClass, Map<SootClass, String> initMap) {
        String initStr;
        if (initMap.containsKey(sootClass)) {
            initStr = initMap.get(sootClass);
        } else {
            initStr = jimpleUtil.getInitMethod(sootClass).toString();
            initMap.put(sootClass, initStr);
        }
        return initStr;
    }

    private void initIOCObjectByPrototype(SootFieldRef sootFieldRef, SootMethod initMethod, String vtype, String declType, String initStr, String callStr) {
        Local tmpRef = jimpleUtil.newLocalVar(vtype.substring(vtype.lastIndexOf(".") + 1).toLowerCase(), RefType.v(vtype));
        Local declRef = jimpleUtil.newLocalVar(declType.substring(declType.lastIndexOf(".") + 1).toLowerCase(), RefType.v(declType));
        JimpleBody body = (JimpleBody) initMethod.retrieveActiveBody();
        Local thisRef = body.getThisLocal();
        if (!body.getLocals().contains(tmpRef)) {
            body.getLocals().add(tmpRef);
        }

        if (!body.getLocals().contains(declRef)) {
            body.getLocals().add(declRef);
        }
        PatchingChain<Unit> units = body.getUnits();
        units.removeIf(unit -> unit instanceof JReturnVoidStmt);
        jimpleUtil.createAssignStmt(declRef, jimpleUtil.createNewExpr(declType), units);
        units.add(jimpleUtil.specialCallStatement(declRef, initStr));
        SootMethod toCall2 = Scene.v().getMethod(callStr);
        jimpleUtil.createAssignStmt(tmpRef, jimpleUtil.createVirtualInvokeExpr(declRef, toCall2), units);
        if (!sootFieldRef.isStatic()) {
            jimpleUtil.createAssignStmt(jimpleUtil.createInstanceFieldRef(thisRef, sootFieldRef), tmpRef, units);
        }
        jimpleUtil.addVoidReturnStmt(units);
    }

    public void initIOCObjectByPrototype(SootFieldRef sootFieldRef, SootMethod initMethod, String vtype, String realType, String initStr) {
        JimpleBody body = (JimpleBody) initMethod.retrieveActiveBody();
        Local tmpRef = jimpleUtil.addLocalVar(vtype.substring(vtype.lastIndexOf(".") + 1).toLowerCase(), vtype, body);
        PatchingChain<Unit> units = body.getUnits();
        units.removeIf(unit -> unit instanceof JReturnVoidStmt);
        jimpleUtil.createAssignStmt(tmpRef, jimpleUtil.createNewExpr(realType), units);
        units.add(jimpleUtil.specialCallStatement(tmpRef, initStr));

        if (!sootFieldRef.isStatic()) {
            jimpleUtil.createAssignStmt(jimpleUtil.createInstanceFieldRef(body.getThisLocal(), sootFieldRef), tmpRef, units);
            jimpleUtil.addVoidReturnStmt(units);
        }
        if (!(units.getLast() instanceof JReturnVoidStmt)) {
            jimpleUtil.addVoidReturnStmt(units);
        }
    }

    public void initIOCObjectBySingleton(SootFieldRef sootFieldRef, SootMethod initMethod, String vtype, SootClass fieldClass) {
        JimpleBody body = (JimpleBody) initMethod.retrieveActiveBody();
        Local thisRef = body.getThisLocal();
        Local tmpRef = jimpleUtil.addLocalVar(vtype.substring(vtype.lastIndexOf(".") + 1).toLowerCase(), vtype, body);
        PatchingChain<Unit> units = body.getUnits();
        units.removeIf(unit -> unit instanceof JReturnVoidStmt);

        String methodName = "get" + fieldClass.getShortName();
        String methodSig = fieldClass.getName() + " " + methodName + "()";
        Value returnValue = null;
        if (singletonFactory.declaresMethod(methodSig)) {
            returnValue = jimpleUtil.createStaticInvokeExpr(singletonFactory.getMethod(methodSig));
        } else if (singletonFactory.declaresMethodByName(methodName)) {
            returnValue = jimpleUtil.createStaticInvokeExpr(singletonFactory.getMethodByName(methodName));
        }
        jimpleUtil.createAssignStmt(tmpRef, returnValue, units);
        if (!sootFieldRef.isStatic()) {
            jimpleUtil.createAssignStmt(jimpleUtil.createInstanceFieldRef(thisRef, sootFieldRef), tmpRef, units);
        }
        jimpleUtil.addVoidReturnStmt(units);
    }
}
